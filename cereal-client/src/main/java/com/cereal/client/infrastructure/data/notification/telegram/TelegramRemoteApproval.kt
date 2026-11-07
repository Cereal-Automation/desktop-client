package com.cereal.client.infrastructure.data.notification.telegram

import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.model.notification.NotificationChannelType
import com.cereal.client.domain.model.notification.NotificationDeliveryStatus
import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.domain.model.task.ConfirmationText
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.domain.repository.NotificationSettingsRepository
import com.cereal.client.infrastructure.data.notification.telegram.model.TelegramUpdate
import com.cereal.sdk.component.userinteraction.ConfirmationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

/**
 * Remote approval: a waiting confirmation as a Telegram message with Confirm and Decline buttons, answerable
 * from the user's phone. Polls `getUpdates` once per bot token, only while a confirmation on it waits.
 *
 * A press acts only when it comes from the chat's own user and names a confirmation waiting right now (a random
 * id per confirmation); anything else is stale and never answers anything.
 */
class TelegramRemoteApproval(
    private val client: TelegramHttpClient,
    private val settings: NotificationSettingsRepository,
    private val scope: CoroutineScope,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val pollTimeoutSeconds: Int = 25,
) {
    private data class Target(
        val botToken: String,
        val chatId: String,
    )

    private class Waiting(
        val target: Target,
        val messageId: Long,
        val text: String,
        val answer: CompletableDeferred<ConfirmationResult>,
    )

    private val logger = LoggerFactory.getLogger(TelegramRemoteApproval::class.java)
    private val lock = Any()
    private val waiting = mutableMapOf<String, Waiting>()
    private val stripping = mutableSetOf<Job>()
    private val pollers = mutableSetOf<String>()
    private val offsets = mutableMapOf<String, Long>()

    /**
     * The task's Telegram (its override, else the global setting when enabled) when the chat is private, the
     * only kind that gets buttons. Private chat ids are positive; groups and channels are negative.
     */
    private suspend fun targetFor(overrides: ScriptNotificationOverrides?): Target? {
        val override = overrides?.telegramOverrides
        if (override == null && !settings.isTelegramEnabled().first()) return null
        val botToken = override?.botToken ?: settings.getTelegramBotToken().first().ifEmpty { return null }
        val chatId = override?.chatId ?: settings.getTelegramChatId().first().ifEmpty { return null }
        return Target(botToken, chatId).takeIf { (chatId.toLongOrNull() ?: 0) > 0 }
    }

    /**
     * Sends [confirmation] with buttons to the task's private Telegram chat; once its answer completes, strips them
     * with how it ended. Returns the send attempt, or null when the task has no such chat.
     */
    suspend fun offer(
        overrides: ScriptNotificationOverrides?,
        confirmation: UserInteraction.Confirmation,
    ): ChannelAttempt? {
        val target = targetFor(overrides) ?: return null
        val id = UUID.randomUUID().toString().replace("-", "")
        val text = text(confirmation)
        val messageId =
            try {
                client.sendMessageWithButtons(
                    target.botToken,
                    target.chatId,
                    text,
                    listOf(confirmation.confirmLabel to "$id:$CONFIRM", confirmation.declineLabel to "$id:$DECLINE"),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("Unable to send the Telegram confirmation.", e)
                return ChannelAttempt(NotificationChannelType.TELEGRAM, NotificationDeliveryStatus.FAILURE, null, e.message)
            }
        val entry = Waiting(target, messageId, text, confirmation.answer)
        synchronized(lock) {
            waiting[id] = entry
            if (pollers.add(target.botToken)) scope.launch { poll(target.botToken) }
        }
        confirmation.answer.invokeOnCompletion { cause -> end(id, entry, cause) }
        return ChannelAttempt(NotificationChannelType.TELEGRAM, NotificationDeliveryStatus.SUCCESS, text, null)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun end(
        id: String,
        entry: Waiting,
        cause: Throwable?,
    ) {
        // A cancelled answer is a stopped task.
        val ending = if (cause == null) ending(entry.answer.getCompleted()) else ConfirmationText.NO_LONGER_WAITING
        synchronized(lock) {
            // Not ours to strip when quitting already did.
            if (waiting.remove(id) == null) return
            // Lazy, so it is tracked before it can finish; quitting waits for it.
            val job = scope.launch(start = CoroutineStart.LAZY) { strip(entry, ending) }
            stripping += job
            job.invokeOnCompletion { synchronized(lock) { stripping -= job } }
            job.start()
        }
    }

    /** At quit: strips every waiting confirmation and waits for strips in flight, best effort within [timeout]. */
    fun stripAll(timeout: Duration = 3.seconds) {
        val (all, inFlight) = synchronized(lock) { waiting.values.toList().also { waiting.clear() } to stripping.toList() }
        if (all.isEmpty() && inFlight.isEmpty()) return
        runBlocking {
            withTimeoutOrNull(timeout) {
                (all.map { scope.launch { strip(it, ConfirmationText.NO_LONGER_WAITING) } } + inFlight).joinAll()
            }
        }
    }

    // ponytail: a poll in flight when the last confirmation ends runs out its timeout; its presses are then stale.
    private suspend fun poll(botToken: String) {
        while (true) {
            val offset =
                synchronized(lock) {
                    if (waiting.values.none { it.target.botToken == botToken }) {
                        pollers.remove(botToken)
                        return
                    }
                    offsets[botToken]
                }
            val updates =
                try {
                    client.getUpdates(botToken, offset, pollTimeoutSeconds)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("Telegram getUpdates failed; retrying.", e)
                    delay(RETRY_DELAY)
                    continue
                }
            for (update in updates) {
                // Consumed either way: the next poll confirms it.
                synchronized(lock) { offsets[botToken] = update.updateId + 1 }
                update.callbackQuery?.let { press(botToken, it) }
            }
        }
    }

    private suspend fun press(
        botToken: String,
        press: TelegramUpdate.CallbackQuery,
    ) {
        val id = press.data?.substringBefore(':')
        val answer =
            when (press.data?.substringAfter(':', "")) {
                CONFIRM -> ConfirmationResult.Confirmed
                DECLINE -> ConfirmationResult.Declined
                else -> null
            }
        val entry = synchronized(lock) { id?.let { waiting[it] } }?.takeIf { it.target.botToken == botToken }
        if (entry != null) {
            // Someone else in the chat, or a malformed answer: ignore, the confirmation keeps waiting.
            if (answer == null || press.from.id.toString() != entry.target.chatId) return
            entry.answer.complete(answer)
            bestEffort { client.answerCallbackQuery(botToken, press.id, null) }
            return
        }
        bestEffort { client.answerCallbackQuery(botToken, press.id, ConfirmationText.STALE_PRESS) }
        val message = press.message ?: return
        message.text?.let { text ->
            bestEffort { client.editMessageText(botToken, message.chat.id.toString(), message.messageId, "$text\n\n${ConfirmationText.NO_LONGER_WAITING}") }
        }
    }

    private suspend fun strip(
        entry: Waiting,
        ending: String,
    ) = bestEffort {
        client.editMessageText(entry.target.botToken, entry.target.chatId, entry.messageId, "${entry.text}\n\n$ending")
    }

    private suspend fun bestEffort(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Telegram call failed; ignored.", e)
        }
    }

    /** The window's content as plain text, with when it expires so an old message reads as dead. */
    @OptIn(ExperimentalTime::class)
    private fun text(confirmation: UserInteraction.Confirmation): String =
        buildList {
            add(confirmation.question)
            addAll(confirmation.details)
            confirmation.url?.let(::add)
            confirmation.expiresAt?.let { add(ConfirmationText.EXPIRES_AT.format(EXPIRY_FORMAT.withZone(zone).format(it.toJavaInstant()))) }
        }.joinToString("\n\n")

    private fun ending(result: ConfirmationResult): String =
        when (result) {
            ConfirmationResult.Confirmed -> ConfirmationText.CONFIRMED
            ConfirmationResult.Declined -> ConfirmationText.DECLINED
            ConfirmationResult.Expired -> ConfirmationText.EXPIRED
        }

    private companion object {
        const val CONFIRM = "c"
        const val DECLINE = "d"
        val RETRY_DELAY = 5.seconds
        val EXPIRY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

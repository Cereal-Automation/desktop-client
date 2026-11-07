package com.cereal.client.infrastructure.data.notification.telegram

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.notification.SendNotificationFromScriptInstanceInteractor
import com.cereal.client.domain.model.notification.NotificationChannelType
import com.cereal.client.domain.model.notification.NotificationDeliveryStatus
import com.cereal.client.domain.model.notification.NotificationResolver
import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.domain.model.notification.TelegramNotificationData
import com.cereal.client.domain.model.notification.TelegramOverrides
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationHistoryRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationSettingsRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import com.cereal.client.infrastructure.provider.RemoteApprovalProviderImpl
import com.cereal.client.infrastructure.provider.inmemory.InMemoryNotificationProvider
import com.cereal.client.infrastructure.sdkcomponent.UserInteractionComponentImpl
import com.cereal.sdk.component.userinteraction.ConfirmationResult
import fixtures.aTask
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Remote approval end to end: the task's [UserInteractionComponentImpl] with a real [TelegramRemoteApproval]
 * talking to a fake Telegram Bot API on a [MockWebServer]. Real time (runBlocking), since the long poll is real HTTP.
 */
class TelegramRemoteApprovalTest {
    private val telegram = FakeTelegram()
    private val server = MockWebServer().apply { dispatcher = telegram }
    private val settings = InMemoryNotificationSettingsRepository()
    private val notificationProvider = InMemoryNotificationProvider()
    private val tasksRepository = InMemoryTasksRepository()
    private val history = InMemoryNotificationHistoryRepository()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val approval =
        TelegramRemoteApproval(
            client = TelegramHttpClient(baseUrl = server.url("").toString().trimEnd('/')),
            settings = settings,
            scope = scope,
            zone = ZoneOffset.UTC,
            pollTimeoutSeconds = 1,
        )

    @AfterEach
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun component(
        taskId: String,
        overrides: ScriptNotificationOverrides? = null,
    ) = UserInteractionComponentImpl(
        tasksRepository,
        taskId,
        CoroutinesDispatcherProvider(),
        SendNotificationFromScriptInstanceInteractor(
            notificationProvider,
            settings,
            history,
            NotificationResolver(),
        ),
        overrides?.let { o -> mockk<ScriptPackageInstance> { every { notificationOverrides } returns o } },
        RemoteApprovalProviderImpl(approval),
    )

    private suspend fun telegramSetUp(chatId: String = CHAT_ID) {
        settings.setTelegramEnabled(true)
        settings.setTelegramBotToken(BOT_TOKEN)
        settings.setTelegramChatId(chatId)
    }

    private suspend fun startAsking(
        taskId: String = "task-1",
        overrides: ScriptNotificationOverrides? = null,
        timeout: Duration = 15.minutes,
    ) = run {
        tasksRepository.addTask(aTask(id = taskId, packageName = "com.example.script", running = true))
        CoroutineScope(Dispatchers.IO).async {
            component(taskId, overrides).requestConfirmation(
                title = "AI Browser Agent",
                question = "Place order on amazon.com?",
                details = listOf("Clicks \"Place your order\".", "Max order total: $50."),
                url = "https://www.amazon.com/checkout",
                confirmLabel = "Yes, place order",
                declineLabel = "No, stop here",
                timeout = timeout,
            )
        }
    }

    private suspend fun shown(taskId: String = "task-1"): UserInteraction.Confirmation =
        tasksRepository
            .getAllTasks()
            .map { tasks -> tasks.firstOrNull { it.id == taskId }?.userInteraction }
            .filterIsInstance<UserInteraction.Confirmation>()
            .first()

    @Test
    fun `a private chat gets the window's content with Confirm and Decline buttons instead of the plain heads-up`() =
        runBlocking {
            telegramSetUp()

            val result = startAsking()
            val sent = telegram.awaitSent()
            val expiresAt = shown().expiresAt!!
            result.cancelAndJoin()

            assertEquals(CHAT_ID, sent.string("chat_id"))
            val hhmm = String.format(java.util.Locale.ROOT, "%02d:%02d", expiresAt.epochSeconds / 3600 % 24, expiresAt.epochSeconds / 60 % 60)
            assertEquals(
                "Place order on amazon.com?\n\nClicks \"Place your order\".\n\nMax order total: $50.\n\n" +
                    "https://www.amazon.com/checkout\n\nExpires at $hhmm",
                sent.string("text"),
            )
            val buttons = sent.buttons()
            assertEquals(listOf("Yes, place order", "No, stop here"), buttons.map { it.first })
            assertTrue(buttons.all { it.second.length <= 64 })
            assertTrue(buttons[0].second != buttons[1].second)
            assertFalse(notificationProvider.sent.any { it is TelegramNotificationData }, "no plain Telegram heads-up")
        }

    @Test
    fun `a group chat gets the plain heads-up without buttons`() =
        runBlocking {
            telegramSetUp(chatId = "-100123")

            val result = startAsking(timeout = 300.milliseconds)

            assertEquals(ConfirmationResult.Expired, result.await())
            assertTrue(notificationProvider.sent.any { it is TelegramNotificationData }, "plain Telegram heads-up")
            assertTrue(telegram.calls.isEmpty(), "no buttons, no polling")
        }

    @Test
    fun `the task's own Telegram override gets buttons even with global Telegram off`() =
        runBlocking {
            val result = startAsking(overrides = ScriptNotificationOverrides(telegramOverrides = TelegramOverrides(BOT_TOKEN, "77")))

            val sent = telegram.awaitSent()
            result.cancelAndJoin()

            assertEquals("77", sent.string("chat_id"))
        }

    @Test
    fun `Confirm from the chat's user resumes the call, closes the window and ends the message with Confirmed`() =
        runBlocking {
            telegramSetUp()
            val result = startAsking()
            val (confirm, _) = telegram.awaitSent().buttons().map { it.second }

            telegram.press(confirm, fromId = CHAT_ID.toLong())

            assertEquals(ConfirmationResult.Confirmed, withTimeout(5.seconds) { result.await() })
            assertNull(tasksRepository.getTask("task-1")?.userInteraction, "window closed")
            val edit = telegram.awaitCall("editMessageText")
            assertEquals(MESSAGE_ID.toString(), edit.string("message_id"))
            assertTrue(edit.string("text").endsWith("Expires at ${edit.string("text").substringAfter("Expires at ").take(5)}\n\nConfirmed"))
            assertTrue(edit.string("text").startsWith("Place order on amazon.com?\n\n"), "content untouched")
            assertFalse("reply_markup" in edit, "buttons stripped")
        }

    @Test
    fun `Decline from the chat's user resumes the call with Declined`() =
        runBlocking {
            telegramSetUp()
            val result = startAsking()
            val (_, decline) = telegram.awaitSent().buttons().map { it.second }

            telegram.press(decline, fromId = CHAT_ID.toLong())

            assertEquals(ConfirmationResult.Declined, withTimeout(5.seconds) { result.await() })
            assertTrue(telegram.awaitCall("editMessageText").string("text").endsWith("\n\nDeclined"))
        }

    @Test
    fun `a press from anyone but the chat's user never answers, and the window's answer strips the buttons`() =
        runBlocking {
            telegramSetUp()
            val result = startAsking()
            val (confirm, _) = telegram.awaitSent().buttons().map { it.second }

            telegram.press(confirm, fromId = 666)
            telegram.awaitCall("getUpdates") { "offset" in it }
            delay(200.milliseconds)
            assertTrue(result.isActive, "still waiting")
            assertTrue(telegram.callsTo("editMessageText").isEmpty(), "buttons kept")
            shown().answer.complete(ConfirmationResult.Declined)

            assertEquals(ConfirmationResult.Declined, withTimeout(5.seconds) { result.await() })
            assertTrue(telegram.awaitCall("editMessageText").string("text").endsWith("\n\nDeclined"))
        }

    @Test
    fun `an expired confirmation ends the message with Expired`() =
        runBlocking {
            telegramSetUp()

            val result = startAsking(timeout = 500.milliseconds)

            assertEquals(ConfirmationResult.Expired, withTimeout(5.seconds) { result.await() })
            assertTrue(telegram.awaitCall("editMessageText").string("text").endsWith("\n\nExpired"))
        }

    @Test
    fun `stopping the task ends the message with No longer waiting`() =
        runBlocking {
            telegramSetUp()
            val result = startAsking()
            telegram.awaitSent()

            result.cancelAndJoin()

            assertTrue(telegram.awaitCall("editMessageText").string("text").endsWith("\n\nNo longer waiting"))
        }

    @Test
    fun `a stale press never answers anything, gets a toast and loses its buttons, and is consumed`() =
        runBlocking {
            telegramSetUp()
            val result = startAsking()
            val (confirm, _) = telegram.awaitSent().buttons().map { it.second }

            val updateId = telegram.press("0123456789abcdef0123456789abcdef:c", fromId = CHAT_ID.toLong(), messageText = "Old question")
            val toast = telegram.awaitCall("answerCallbackQuery")
            val edit = telegram.awaitCall("editMessageText")
            telegram.awaitCall("getUpdates") { it["offset"]?.toString() == "${updateId + 1}" }

            assertEquals("This confirmation is no longer waiting", toast.string("text"))
            assertEquals("99", edit.string("message_id"))
            assertEquals("Old question\n\nNo longer waiting", edit.string("text"))
            assertTrue(result.isActive, "the waiting confirmation is untouched")
            // A press for the waiting one with a forged answer acts on nothing either.
            telegram.press(confirm.substringBefore(':') + ":x", fromId = CHAT_ID.toLong())
            delay(300.milliseconds)
            assertTrue(result.isActive)
            result.cancelAndJoin()
        }

    @Test
    fun `polls once per bot token and only while a confirmation waits`() =
        runBlocking {
            delay(200.milliseconds)
            assertTrue(telegram.calls.isEmpty(), "idle: no calls")
            telegramSetUp()

            val first = startAsking(taskId = "task-1")
            val second = startAsking(taskId = "task-2")
            telegram.awaitSent()
            telegram.awaitSent()
            delay(500.milliseconds)
            first.cancelAndJoin()
            second.cancelAndJoin()
            delay(500.milliseconds)
            val polls = telegram.callsTo("getUpdates").size
            delay(500.milliseconds)

            assertEquals(1, telegram.maxConcurrentPolls.get())
            assertEquals(polls, telegram.callsTo("getUpdates").size, "stopped polling")
        }

    @Test
    fun `quitting strips the buttons of every waiting confirmation`() =
        runBlocking {
            telegramSetUp()
            val result = startAsking()
            telegram.awaitSent()
            telegram.awaitCall("getUpdates") // registered and waiting

            approval.stripAll()

            assertTrue(
                telegram
                    .callsTo("editMessageText")
                    .single()
                    .string("text")
                    .endsWith("\n\nNo longer waiting"),
            )
            result.cancelAndJoin()
            delay(200.milliseconds)
            assertEquals(1, telegram.callsTo("editMessageText").size, "stripped once")
        }

    @Test
    fun `quitting right after a confirmation ends waits for its message to be stripped`() =
        runBlocking {
            telegramSetUp()
            telegram.editDelayMillis = 500
            val result = startAsking()
            telegram.awaitSent()
            telegram.awaitCall("getUpdates")
            shown().answer.complete(ConfirmationResult.Declined)
            result.await()

            approval.stripAll()

            assertEquals(1, telegram.editsDone.get(), "stripped before quit returns")
        }

    @Test
    fun `a failed button message is recorded and Telegram gets the plain heads-up instead`() =
        runBlocking {
            telegramSetUp()
            telegram.failButtons = true

            val result = startAsking()
            val headsUp = withTimeout(5.seconds) { history.observeByTaskId("task-1").first { it.isNotEmpty() }.single() }
            result.cancelAndJoin()

            val telegramAttempts = history.observeAttempts(headsUp.id).first().filter { it.channel == NotificationChannelType.TELEGRAM }
            assertTrue(telegramAttempts.any { it.status == NotificationDeliveryStatus.FAILURE }, "button send recorded")
            assertTrue(notificationProvider.sent.any { it is TelegramNotificationData }, "plain Telegram heads-up")
        }

    companion object {
        const val BOT_TOKEN = "123456:ABC-def_ghi"
        const val CHAT_ID = "42"
        const val MESSAGE_ID = 7L
    }
}

/** A fake Telegram Bot API: records every call, serves pushed updates to `getUpdates`, and counts concurrent polls. */
class FakeTelegram : Dispatcher() {
    val calls = CopyOnWriteArrayList<Pair<String, JsonObject>>()
    private val sentMessages = LinkedBlockingQueue<JsonObject>()
    private val updates = ConcurrentLinkedQueue<String>()
    private val polling = AtomicInteger()
    val maxConcurrentPolls = AtomicInteger()
    private var nextUpdateId = 100L

    @Volatile var editDelayMillis = 0L

    @Volatile var failButtons = false
    val editsDone = AtomicInteger()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val method = request.path!!.substringAfterLast('/')
        val body = Json.parseToJsonElement(request.body.readUtf8().ifEmpty { "{}" }).jsonObject
        calls += method to body
        return when (method) {
            "sendMessage" -> {
                if (failButtons && "reply_markup" in body) {
                    return MockResponse().setResponseCode(400).setBody("""{"ok":false,"description":"Bad Request"}""")
                }
                sentMessages += body
                ok("""{"message_id":${TelegramRemoteApprovalTest.MESSAGE_ID},"chat":{"id":${body.string("chat_id")}}}""")
            }

            "getUpdates" -> {
                maxConcurrentPolls.accumulateAndGet(polling.incrementAndGet(), ::maxOf)
                try {
                    // A short long-poll: answer as soon as an update is pushed, else empty after a moment.
                    repeat(20) {
                        updates.poll()?.let { return ok("[$it]") }
                        Thread.sleep(10)
                    }
                    ok("[]")
                } finally {
                    polling.decrementAndGet()
                }
            }

            "editMessageText" -> {
                Thread.sleep(editDelayMillis)
                editsDone.incrementAndGet()
                ok("true")
            }

            else -> {
                ok("true")
            }
        }
    }

    private fun ok(result: String) = MockResponse().setBody("""{"ok":true,"result":$result}""")

    fun awaitSent(): JsonObject = sentMessages.poll(5, TimeUnit.SECONDS) ?: error("No sendMessage")

    fun press(
        callbackData: String,
        fromId: Long,
        messageText: String = "old message",
    ): Long {
        val updateId = nextUpdateId++
        updates +=
            """{"update_id":$updateId,"callback_query":{"id":"cb$updateId","from":{"id":$fromId},""" +
            """"message":{"message_id":99,"chat":{"id":$fromId},"text":${Json.encodeToString(messageText)}},""" +
            """"data":"$callbackData"}}"""
        return updateId
    }

    fun callsTo(method: String) = calls.filter { it.first == method }.map { it.second }

    suspend fun awaitCall(
        method: String,
        predicate: (JsonObject) -> Boolean = { true },
    ): JsonObject =
        withTimeout(5.seconds) {
            while (true) {
                callsTo(method).firstOrNull(predicate)?.let { return@withTimeout it }
                delay(10.milliseconds)
            }
            @Suppress("UNREACHABLE_CODE")
            awaitCancellation()
        }
}

fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

/** The inline keyboard's (label, callback_data) pairs. */
fun JsonObject.buttons(): List<Pair<String, String>> =
    getValue("reply_markup")
        .jsonObject
        .getValue("inline_keyboard")
        .jsonArray
        .flatMap { it.jsonArray }
        .map { it.jsonObject.string("text") to it.jsonObject.string("callback_data") }

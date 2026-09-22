package com.cereal.client.application.interactor.notification

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.model.notification.ChannelResolution
import com.cereal.client.domain.model.notification.GlobalNotificationConfig
import com.cereal.client.domain.model.notification.NotificationChannelType
import com.cereal.client.domain.model.notification.NotificationDeliveryStatus
import com.cereal.client.domain.model.notification.NotificationResolver
import com.cereal.client.domain.model.notification.ScriptNotification
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.provider.NotificationProvider
import com.cereal.client.domain.repository.NotificationHistoryRepository
import com.cereal.client.domain.repository.NotificationSettingsRepository
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException

/**
 * Imperative shell around [NotificationResolver]: reads the global settings snapshot, asks the
 * resolver which channels to send and how, then sends each and records every attempt. The decision
 * of *what* to send on each channel — selection, priority, validation — lives in the resolver; this
 * interactor only performs I/O and bookkeeping.
 */
class SendNotificationFromScriptInstanceInteractor(
    private val notificationRepository: NotificationProvider,
    private val notificationSettingsRepository: NotificationSettingsRepository,
    private val notificationHistoryRepository: NotificationHistoryRepository,
    private val notificationResolver: NotificationResolver,
) : Interactor<Unit, SendNotificationFromScriptInstanceInteractor.Params>() {
    override suspend fun run(params: Params) {
        val resolutions =
            notificationResolver.resolve(
                request = params.notification,
                overrides = params.scriptPackageInstance?.notificationOverrides,
                config = readGlobalConfig(),
            )

        val attempts = mutableListOf<ChannelAttempt>()
        try {
            for (resolution in resolutions) {
                attempts +=
                    when (resolution) {
                        is ChannelResolution.Resolved -> {
                            sendAndCapture(resolution.channel) {
                                notificationRepository.sendNotification(resolution.data)
                                resolution.payloadPreview
                            }
                        }

                        is ChannelResolution.MissingConfig -> {
                            ChannelAttempt(
                                channel = resolution.channel,
                                status = NotificationDeliveryStatus.FAILURE,
                                payload = null,
                                errorMessage = resolution.reason,
                            )
                        }
                    }
            }
        } finally {
            // Always persist whatever was attempted. Resolution-time failures arrive as MissingConfig
            // (recorded as FAILURE above) and send-time failures are captured per channel inside
            // sendAndCapture, so a failure on one channel never skips later channels or goes unrecorded.
            notificationHistoryRepository.record(
                taskId = params.taskId,
                title = params.notification.title,
                message = params.notification.message,
                timestamp = System.currentTimeMillis(),
                attempts = attempts,
            )
        }
    }

    private suspend fun readGlobalConfig(): GlobalNotificationConfig =
        GlobalNotificationConfig(
            discord =
                GlobalNotificationConfig.Discord(
                    enabled = notificationSettingsRepository.isDiscordWebhookEnabled().first(),
                    webhookUrl = notificationSettingsRepository.getDiscordWebhookUrl().first().ifEmpty { null },
                ),
            telegram =
                GlobalNotificationConfig.Telegram(
                    enabled = notificationSettingsRepository.isTelegramEnabled().first(),
                    botToken = notificationSettingsRepository.getTelegramBotToken().first().ifEmpty { null },
                    chatId = notificationSettingsRepository.getTelegramChatId().first().ifEmpty { null },
                ),
            email =
                GlobalNotificationConfig.Email(
                    enabled = notificationSettingsRepository.isEmailEnabled().first(),
                    smtpHost = notificationSettingsRepository.getEmailSmtpHost().first().ifEmpty { null },
                    smtpPort = notificationSettingsRepository.getEmailSmtpPort().first().takeIf { it > 0 },
                    username = notificationSettingsRepository.getEmailUsername().first().ifEmpty { null },
                    password = notificationSettingsRepository.getEmailPassword().first().ifEmpty { null },
                    from = notificationSettingsRepository.getEmailFrom().first().ifEmpty { null },
                    to = notificationSettingsRepository.getEmailTo().first().ifEmpty { null },
                    useTls = notificationSettingsRepository.getEmailUseTls().first(),
                ),
            desktopEnabled = notificationSettingsRepository.isDesktopNotificationsEnabled().first(),
        )

    /**
     * Sends one resolved channel and captures the outcome as a [ChannelAttempt]. [block] performs
     * the send and returns the public payload recorded on success. A send-time failure is captured
     * as a FAILURE attempt so it never aborts the remaining channels.
     */
    private suspend fun sendAndCapture(
        channel: NotificationChannelType,
        block: suspend () -> String?,
    ): ChannelAttempt =
        try {
            val payload = block()
            ChannelAttempt(
                channel = channel,
                status = NotificationDeliveryStatus.SUCCESS,
                payload = payload,
                errorMessage = null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ChannelAttempt(
                channel = channel,
                status = NotificationDeliveryStatus.FAILURE,
                payload = null,
                errorMessage = e.message,
            )
        }

    data class Params(
        val notification: ScriptNotification,
        val taskId: String,
        val scriptPackageInstance: ScriptPackageInstance? = null,
    )
}

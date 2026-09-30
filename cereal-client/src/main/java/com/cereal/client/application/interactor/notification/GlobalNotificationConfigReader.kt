package com.cereal.client.application.interactor.notification

import com.cereal.client.domain.model.notification.GlobalNotificationConfig
import com.cereal.client.domain.repository.NotificationSettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Reads the stored global notification settings as the snapshot a send resolves against.
 *
 * @param desktopChannelAvailable false in headless mode: there is no tray to deliver to, so the
 *   system channel is left out of the snapshot while the stored preference stays untouched.
 */
class GlobalNotificationConfigReader(
    private val notificationSettingsRepository: NotificationSettingsRepository,
    private val desktopChannelAvailable: Boolean = true,
) {
    suspend fun read(): GlobalNotificationConfig =
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
            desktopEnabled = desktopChannelAvailable && notificationSettingsRepository.isDesktopNotificationsEnabled().first(),
        )
}

package com.cereal.client.application.interactor.settings.notifications

import com.cereal.client.application.Interactor
import com.cereal.client.application.SensitiveParams
import com.cereal.client.domain.repository.NotificationSettingsRepository

class SaveAllNotificationSettingsInteractor(
    private val notificationSettingsRepository: NotificationSettingsRepository,
) : Interactor<Unit, SaveAllNotificationSettingsInteractor.Params>() {
    override suspend fun run(params: Params) {
        // Save Discord settings
        params.discordEnabled?.let { enabled ->
            notificationSettingsRepository.setDiscordWebhookEnabled(enabled)
        }
        params.discordWebhookUrl?.let { url ->
            notificationSettingsRepository.setDiscordWebhookUrl(url)
        }

        // Save Telegram settings
        params.telegramEnabled?.let { enabled ->
            notificationSettingsRepository.setTelegramEnabled(enabled)
        }
        params.telegramBotToken?.let { token ->
            notificationSettingsRepository.setTelegramBotToken(token)
        }
        params.telegramChatId?.let { chatId ->
            notificationSettingsRepository.setTelegramChatId(chatId)
        }

        // Save Desktop notification settings
        params.desktopNotificationsEnabled?.let { enabled ->
            notificationSettingsRepository.setDesktopNotificationsEnabled(enabled)
        }

        // Save Email settings
        params.emailEnabled?.let { enabled ->
            notificationSettingsRepository.setEmailEnabled(enabled)
        }
        params.emailSmtpHost?.let { host ->
            notificationSettingsRepository.setEmailSmtpHost(host)
        }
        params.emailSmtpPort?.let { port ->
            notificationSettingsRepository.setEmailSmtpPort(port)
        }
        params.emailUsername?.let { username ->
            notificationSettingsRepository.setEmailUsername(username)
        }
        params.emailPassword?.let { password ->
            notificationSettingsRepository.setEmailPassword(password)
        }
        params.emailFrom?.let { from ->
            notificationSettingsRepository.setEmailFrom(from)
        }
        params.emailTo?.let { to ->
            notificationSettingsRepository.setEmailTo(to)
        }
        params.emailUseTls?.let { useTls ->
            notificationSettingsRepository.setEmailUseTls(useTls)
        }
    }

    data class Params(
        val discordEnabled: Boolean? = null,
        // NB: carries webhook URLs / bot tokens / SMTP password — see [SensitiveParams].
        val discordWebhookUrl: String? = null,
        val telegramEnabled: Boolean? = null,
        val telegramBotToken: String? = null,
        val telegramChatId: String? = null,
        val desktopNotificationsEnabled: Boolean? = null,
        val emailEnabled: Boolean? = null,
        val emailSmtpHost: String? = null,
        val emailSmtpPort: Int? = null,
        val emailUsername: String? = null,
        val emailPassword: String? = null,
        val emailFrom: String? = null,
        val emailTo: String? = null,
        val emailUseTls: Boolean? = null,
    ) : SensitiveParams
}

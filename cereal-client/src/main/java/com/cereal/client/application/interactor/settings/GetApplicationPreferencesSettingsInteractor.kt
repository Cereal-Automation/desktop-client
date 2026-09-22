package com.cereal.client.application.interactor.settings

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.settings.ApplicationPreferenceSettings
import com.cereal.client.domain.repository.ApplicationPreferenceRepository
import com.cereal.client.domain.repository.NotificationSettingsRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@OptIn(FlowPreview::class)
class GetApplicationPreferencesSettingsInteractor(
    private val applicationPreferenceRepository: ApplicationPreferenceRepository,
    private val notificationSettingsRepository: NotificationSettingsRepository,
) : FlowInteractor<ApplicationPreferenceSettings, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<ApplicationPreferenceSettings> =
        combine(
            basicSettingsFlow(),
            telegramSettingsFlow(),
            emailSettingsFlow(),
            applicationPreferenceRepository.isShowDebugLogs(),
            applicationPreferenceRepository.getProxyHealthCheckInterval(),
        ) { basic, telegram, email, showDebugLogs, proxyHealthCheckInterval ->
            ApplicationPreferenceSettings(
                discordActivityStatusEnabled = basic.discordActivityStatusEnabled,
                desktopNotificationsEnabled = basic.desktopNotificationsEnabled,
                discordWebhookEnabled = basic.discordWebhookEnabled,
                discordWebhookUrl = basic.discordWebhookUrl,
                developmentScriptsEnabled = basic.developmentScriptsEnabled,
                telegramEnabled = telegram.enabled,
                telegramBotToken = telegram.botToken,
                telegramChatId = telegram.chatId,
                emailEnabled = email.enabled,
                emailSmtpHost = email.smtpHost,
                emailSmtpPort = email.smtpPort,
                emailUsername = email.username,
                emailPassword = email.password,
                emailFrom = email.from,
                emailTo = email.to,
                emailUseTls = email.useTls,
                showDebugLogs = showDebugLogs,
                proxyHealthCheckInterval = proxyHealthCheckInterval,
            )
        }

    private suspend fun basicSettingsFlow(): Flow<BasicSettings> =
        combine(
            applicationPreferenceRepository.isDiscordActivityStatusEnabled(),
            notificationSettingsRepository.isDesktopNotificationsEnabled(),
            notificationSettingsRepository.isDiscordWebhookEnabled(),
            notificationSettingsRepository.getDiscordWebhookUrl(),
            applicationPreferenceRepository.isDevelopmentScriptsEnabled(),
        ) { discordActivityStatus, desktopNotifications, discordWebhookEnabled, discordWebhookUrl, developmentScriptsEnabled ->
            BasicSettings(
                discordActivityStatusEnabled = discordActivityStatus,
                desktopNotificationsEnabled = desktopNotifications,
                discordWebhookEnabled = discordWebhookEnabled,
                discordWebhookUrl = discordWebhookUrl,
                developmentScriptsEnabled = developmentScriptsEnabled,
            )
        }

    private suspend fun telegramSettingsFlow(): Flow<TelegramSettings> =
        combine(
            notificationSettingsRepository.isTelegramEnabled(),
            notificationSettingsRepository.getTelegramBotToken(),
            notificationSettingsRepository.getTelegramChatId(),
        ) { enabled, botToken, chatId ->
            TelegramSettings(
                enabled = enabled,
                botToken = botToken,
                chatId = chatId,
            )
        }

    private suspend fun emailSettingsFlow(): Flow<EmailSettings> =
        combine(
            combine(
                notificationSettingsRepository.isEmailEnabled(),
                notificationSettingsRepository.getEmailSmtpHost(),
                notificationSettingsRepository.getEmailSmtpPort(),
                notificationSettingsRepository.getEmailUsername(),
                notificationSettingsRepository.getEmailPassword(),
            ) { enabled, smtpHost, smtpPort, username, password ->
                EmailSettings(
                    enabled = enabled,
                    smtpHost = smtpHost,
                    smtpPort = smtpPort,
                    username = username,
                    password = password,
                    from = "",
                    to = "",
                    useTls = true,
                )
            },
            notificationSettingsRepository.getEmailFrom(),
            notificationSettingsRepository.getEmailTo(),
            notificationSettingsRepository.getEmailUseTls(),
        ) { email, from, to, useTls ->
            email.copy(
                from = from,
                to = to,
                useTls = useTls,
            )
        }

    private data class BasicSettings(
        val discordActivityStatusEnabled: Boolean,
        val desktopNotificationsEnabled: Boolean,
        val discordWebhookEnabled: Boolean,
        val discordWebhookUrl: String?,
        val developmentScriptsEnabled: Boolean,
    )

    private data class TelegramSettings(
        val enabled: Boolean,
        val botToken: String?,
        val chatId: String?,
    )

    private data class EmailSettings(
        val enabled: Boolean,
        val smtpHost: String?,
        val smtpPort: Int,
        val username: String?,
        val password: String?,
        val from: String?,
        val to: String?,
        val useTls: Boolean,
    )
}

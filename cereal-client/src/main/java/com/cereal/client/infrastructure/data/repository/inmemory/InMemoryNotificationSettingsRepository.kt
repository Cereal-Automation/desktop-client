package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.settings.ApplicationPreferenceSettings
import com.cereal.client.domain.model.settings.ProxyHealthCheckInterval
import com.cereal.client.domain.repository.NotificationSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [NotificationSettingsRepository] for screen and interactor tests. Every setting is
 * backed by a [MutableStateFlow] so getters return live values and setters update them.
 *
 * One getter/setter pair per notification field, so the function count is inherently high.
 */
@Suppress("TooManyFunctions")
class InMemoryNotificationSettingsRepository : NotificationSettingsRepository {
    private val desktopNotificationsEnabled = MutableStateFlow(true)
    private val discordWebhookEnabled = MutableStateFlow(false)
    private val discordWebhookUrl = MutableStateFlow("")
    private val telegramEnabled = MutableStateFlow(false)
    private val telegramBotToken = MutableStateFlow("")
    private val telegramChatId = MutableStateFlow("")
    private val emailEnabled = MutableStateFlow(false)
    private val emailSmtpHost = MutableStateFlow("")
    private val emailSmtpPort = MutableStateFlow(DEFAULT_SMTP_PORT)
    private val emailUsername = MutableStateFlow("")
    private val emailPassword = MutableStateFlow("")
    private val emailFrom = MutableStateFlow("")
    private val emailTo = MutableStateFlow("")
    private val emailUseTls = MutableStateFlow(true)
    private val notificationCenterLastSeenAt = MutableStateFlow(0L)

    override suspend fun getApplicationPreferenceSettings(): ApplicationPreferenceSettings =
        ApplicationPreferenceSettings(
            desktopNotificationsEnabled = desktopNotificationsEnabled.value,
            developmentScriptsEnabled = false,
            discordActivityStatusEnabled = true,
            discordWebhookEnabled = discordWebhookEnabled.value,
            discordWebhookUrl = discordWebhookUrl.value,
            telegramEnabled = telegramEnabled.value,
            telegramBotToken = telegramBotToken.value,
            telegramChatId = telegramChatId.value,
            emailEnabled = emailEnabled.value,
            emailSmtpHost = emailSmtpHost.value,
            emailSmtpPort = emailSmtpPort.value,
            emailUsername = emailUsername.value,
            emailPassword = emailPassword.value,
            emailFrom = emailFrom.value,
            emailTo = emailTo.value,
            emailUseTls = emailUseTls.value,
            showDebugLogs = false,
            proxyHealthCheckInterval = ProxyHealthCheckInterval.Default,
        )

    override suspend fun setDesktopNotificationsEnabled(enabled: Boolean) {
        desktopNotificationsEnabled.value = enabled
    }

    override suspend fun isDesktopNotificationsEnabled(): Flow<Boolean> = desktopNotificationsEnabled

    override suspend fun setDiscordWebhookEnabled(enabled: Boolean) {
        discordWebhookEnabled.value = enabled
    }

    override suspend fun isDiscordWebhookEnabled(): Flow<Boolean> = discordWebhookEnabled

    override suspend fun setDiscordWebhookUrl(url: String) {
        discordWebhookUrl.value = url
    }

    override suspend fun getDiscordWebhookUrl(): Flow<String> = discordWebhookUrl

    override suspend fun setTelegramEnabled(enabled: Boolean) {
        telegramEnabled.value = enabled
    }

    override suspend fun isTelegramEnabled(): Flow<Boolean> = telegramEnabled

    override suspend fun setTelegramBotToken(token: String) {
        telegramBotToken.value = token
    }

    override suspend fun getTelegramBotToken(): Flow<String> = telegramBotToken

    override suspend fun setTelegramChatId(chatId: String) {
        telegramChatId.value = chatId
    }

    override suspend fun getTelegramChatId(): Flow<String> = telegramChatId

    override suspend fun setEmailEnabled(enabled: Boolean) {
        emailEnabled.value = enabled
    }

    override suspend fun isEmailEnabled(): Flow<Boolean> = emailEnabled

    override suspend fun setEmailSmtpHost(host: String) {
        emailSmtpHost.value = host
    }

    override suspend fun getEmailSmtpHost(): Flow<String> = emailSmtpHost

    override suspend fun setEmailSmtpPort(port: Int) {
        emailSmtpPort.value = port
    }

    override suspend fun getEmailSmtpPort(): Flow<Int> = emailSmtpPort

    override suspend fun setEmailUsername(username: String) {
        emailUsername.value = username
    }

    override suspend fun getEmailUsername(): Flow<String> = emailUsername

    override suspend fun setEmailPassword(password: String) {
        emailPassword.value = password
    }

    override suspend fun getEmailPassword(): Flow<String> = emailPassword

    override suspend fun setEmailFrom(from: String) {
        emailFrom.value = from
    }

    override suspend fun getEmailFrom(): Flow<String> = emailFrom

    override suspend fun setEmailTo(to: String) {
        emailTo.value = to
    }

    override suspend fun getEmailTo(): Flow<String> = emailTo

    override suspend fun setEmailUseTls(useTls: Boolean) {
        emailUseTls.value = useTls
    }

    override suspend fun getEmailUseTls(): Flow<Boolean> = emailUseTls

    override suspend fun setNotificationCenterLastSeenAt(timestamp: Long) {
        notificationCenterLastSeenAt.value = timestamp
    }

    override suspend fun getNotificationCenterLastSeenAt(): Flow<Long> = notificationCenterLastSeenAt

    private companion object {
        const val DEFAULT_SMTP_PORT = 587
    }
}

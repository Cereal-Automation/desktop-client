package com.cereal.client.domain.model.settings

data class ApplicationPreferenceSettings(
    val desktopNotificationsEnabled: Boolean,
    val developmentScriptsEnabled: Boolean,
    val discordActivityStatusEnabled: Boolean,
    val discordWebhookEnabled: Boolean,
    val discordWebhookUrl: String?,
    val telegramEnabled: Boolean,
    val telegramBotToken: String?,
    val telegramChatId: String?,
    val emailEnabled: Boolean,
    val emailSmtpHost: String?,
    val emailSmtpPort: Int,
    val emailUsername: String?,
    val emailPassword: String?,
    val emailFrom: String?,
    val emailTo: String?,
    val emailUseTls: Boolean,
    val showDebugLogs: Boolean,
    val proxyHealthCheckInterval: ProxyHealthCheckInterval,
) {
    /**
     * Checks if all external notification channels (Discord, Telegram, Email) are disabled.
     * @return true if no external channels are configured, false otherwise
     */
    fun hasNoExternalNotificationChannels(): Boolean = !discordWebhookEnabled && !telegramEnabled && !emailEnabled
}

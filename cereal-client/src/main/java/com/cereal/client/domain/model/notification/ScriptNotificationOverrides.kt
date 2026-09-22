package com.cereal.client.domain.model.notification

/**
 * Domain model representing notification channel setting overrides for a specific script.
 * When a script sends a notification, these settings take precedence over global settings
 * if they are specified (non-null).
 */
data class ScriptNotificationOverrides(
    val discordOverrides: DiscordOverrides? = null,
    val telegramOverrides: TelegramOverrides? = null,
    val emailOverrides: EmailOverrides? = null,
) {
    /**
     * Returns true if any override is configured.
     */
    fun hasAnyOverrides(): Boolean = discordOverrides != null || telegramOverrides != null || emailOverrides != null
}

/**
 * Discord notification settings override for a script.
 */
data class DiscordOverrides(
    val webhookUrl: String,
)

/**
 * Telegram notification settings override for a script.
 */
data class TelegramOverrides(
    val botToken: String,
    val chatId: String,
)

/**
 * Email notification settings override for a script.
 */
data class EmailOverrides(
    val smtpHost: String,
    val smtpPort: Int,
    val username: String,
    val password: String,
    val from: String,
    val to: String,
    val useTls: Boolean,
)

package com.cereal.client.domain.repository

import com.cereal.client.domain.model.settings.ApplicationPreferenceSettings
import kotlinx.coroutines.flow.Flow

/**
 * Owns the user's notification settings: the enable flags and field values for every delivery
 * channel (desktop, Discord webhook, Telegram, Email) plus the notification-center "last seen"
 * watermark. App-wide toggles that are not about notifications live on
 * [ApplicationPreferenceRepository], and vendor credentials on their own dedicated repositories.
 *
 * One getter/setter pair per notification field, so the function count is inherently high.
 */
@Suppress("TooManyFunctions")
interface NotificationSettingsRepository {
    /**
     * Reads a snapshot of the current settings. The notification fields are the authoritative
     * concern here; the few app-wide fields on [ApplicationPreferenceSettings] are included so a
     * single call can back the whole settings surface.
     */
    suspend fun getApplicationPreferenceSettings(): ApplicationPreferenceSettings

    suspend fun setDesktopNotificationsEnabled(enabled: Boolean)

    suspend fun isDesktopNotificationsEnabled(): Flow<Boolean>

    suspend fun setDiscordWebhookEnabled(enabled: Boolean)

    suspend fun isDiscordWebhookEnabled(): Flow<Boolean>

    suspend fun setDiscordWebhookUrl(url: String)

    suspend fun getDiscordWebhookUrl(): Flow<String>

    suspend fun setTelegramEnabled(enabled: Boolean)

    suspend fun isTelegramEnabled(): Flow<Boolean>

    suspend fun setTelegramBotToken(token: String)

    suspend fun getTelegramBotToken(): Flow<String>

    suspend fun setTelegramChatId(chatId: String)

    suspend fun getTelegramChatId(): Flow<String>

    suspend fun setEmailEnabled(enabled: Boolean)

    suspend fun isEmailEnabled(): Flow<Boolean>

    suspend fun setEmailSmtpHost(host: String)

    suspend fun getEmailSmtpHost(): Flow<String>

    suspend fun setEmailSmtpPort(port: Int)

    suspend fun getEmailSmtpPort(): Flow<Int>

    suspend fun setEmailUsername(username: String)

    suspend fun getEmailUsername(): Flow<String>

    suspend fun setEmailPassword(password: String)

    suspend fun getEmailPassword(): Flow<String>

    suspend fun setEmailFrom(from: String)

    suspend fun getEmailFrom(): Flow<String>

    suspend fun setEmailTo(to: String)

    suspend fun getEmailTo(): Flow<String>

    suspend fun setEmailUseTls(useTls: Boolean)

    suspend fun getEmailUseTls(): Flow<Boolean>

    /**
     * The timestamp (epoch millis) of the newest notification the user had seen the last time the
     * Notification center was the active screen. Notifications newer than this are "unseen" and
     * drive the sidebar badge. Defaults to 0 (everything unseen) for a fresh user.
     */
    suspend fun setNotificationCenterLastSeenAt(timestamp: Long)

    suspend fun getNotificationCenterLastSeenAt(): Flow<Long>
}

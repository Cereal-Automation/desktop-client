package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.settings.ApplicationPreferenceSettings
import com.cereal.client.domain.model.settings.ProxyHealthCheckInterval
import com.cereal.client.domain.repository.NotificationSettingsRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.KeyValueDataSource
import com.cereal.client.infrastructure.data.datasource.preference.ApplicationPreferenceKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// One getter/setter pair per notification field, so the function count is inherently high.
@Suppress("TooManyFunctions")
class NotificationSettingsRepositoryImpl(
    private val keyValueDataSource: KeyValueDataSource,
    private val userSession: UserSession,
) : NotificationSettingsRepository {
    override suspend fun getApplicationPreferenceSettings(): ApplicationPreferenceSettings =
        ApplicationPreferenceSettings(
            desktopNotificationsEnabled = isDesktopNotificationsEnabled().first(),
            developmentScriptsEnabled = booleanSetting(ApplicationPreferenceKey.DevelopmentScriptsEnabled),
            discordActivityStatusEnabled = booleanSetting(ApplicationPreferenceKey.DiscordActivityStatusEnabled),
            discordWebhookEnabled = isDiscordWebhookEnabled().first(),
            discordWebhookUrl = getDiscordWebhookUrl().first(),
            telegramEnabled = isTelegramEnabled().first(),
            telegramBotToken = getTelegramBotToken().first(),
            telegramChatId = getTelegramChatId().first(),
            emailEnabled = isEmailEnabled().first(),
            emailSmtpHost = getEmailSmtpHost().first(),
            emailSmtpPort = getEmailSmtpPort().first(),
            emailUsername = getEmailUsername().first(),
            emailPassword = getEmailPassword().first(),
            emailFrom = getEmailFrom().first(),
            emailTo = getEmailTo().first(),
            emailUseTls = getEmailUseTls().first(),
            showDebugLogs = booleanSetting(ApplicationPreferenceKey.ShowDebugLogs),
            proxyHealthCheckInterval =
                keyValueDataSource
                    .getStringByKey(ApplicationPreferenceKey.ProxyHealthCheckInterval.key, userSession.requireUser())
                    .map { ProxyHealthCheckInterval.fromStorageValue(it) }
                    .first(),
        )

    private suspend fun booleanSetting(key: ApplicationPreferenceKey<Boolean>): Boolean = keyValueDataSource.getBooleanByKey(key.key, userSession.requireUser()).first() ?: key.defaultValue

    override suspend fun setDesktopNotificationsEnabled(enabled: Boolean) {
        val key = ApplicationPreferenceKey.DesktopNotificationsEnabled.key
        keyValueDataSource.setBooleanByKey(key, enabled, userSession.requireUser())
    }

    override suspend fun isDesktopNotificationsEnabled(): Flow<Boolean> {
        val key = ApplicationPreferenceKey.DesktopNotificationsEnabled.key
        val defaultValue = ApplicationPreferenceKey.DesktopNotificationsEnabled.defaultValue

        return keyValueDataSource.getBooleanByKey(key, userSession.requireUser()).map {
            it ?: defaultValue
        }
    }

    override suspend fun setDiscordWebhookEnabled(enabled: Boolean) {
        val key = ApplicationPreferenceKey.DiscordWebhookEnabled.key

        keyValueDataSource.setBooleanByKey(key, enabled, userSession.requireUser())
    }

    override suspend fun isDiscordWebhookEnabled(): Flow<Boolean> {
        val key = ApplicationPreferenceKey.DiscordWebhookEnabled.key
        val defaultValue = ApplicationPreferenceKey.DiscordWebhookEnabled.defaultValue

        return keyValueDataSource
            .getBooleanByKey(key, userSession.requireUser())
            .map { it ?: defaultValue }
    }

    override suspend fun setDiscordWebhookUrl(url: String) {
        val key = ApplicationPreferenceKey.DiscordWebhookUrl.key

        keyValueDataSource.setStringByKey(key, url, userSession.requireUser())
    }

    override suspend fun getDiscordWebhookUrl(): Flow<String> {
        val key = ApplicationPreferenceKey.DiscordWebhookUrl.key
        val defaultValue = ApplicationPreferenceKey.DiscordWebhookUrl.defaultValue

        return keyValueDataSource
            .getStringByKey(key, userSession.requireUser())
            .map { it ?: defaultValue }
    }

    override suspend fun setTelegramEnabled(enabled: Boolean) {
        val key = ApplicationPreferenceKey.TelegramEnabled.key
        keyValueDataSource.setBooleanByKey(key, enabled, userSession.requireUser())
    }

    override suspend fun isTelegramEnabled(): Flow<Boolean> {
        val key = ApplicationPreferenceKey.TelegramEnabled.key
        val defaultValue = ApplicationPreferenceKey.TelegramEnabled.defaultValue

        return keyValueDataSource.getBooleanByKey(key, userSession.requireUser()).map {
            it ?: defaultValue
        }
    }

    override suspend fun setTelegramBotToken(token: String) {
        val key = ApplicationPreferenceKey.TelegramBotToken.key
        keyValueDataSource.setStringByKey(key, token, userSession.requireUser())
    }

    override suspend fun getTelegramBotToken(): Flow<String> {
        val key = ApplicationPreferenceKey.TelegramBotToken.key
        val defaultValue = ApplicationPreferenceKey.TelegramBotToken.defaultValue

        return keyValueDataSource.getStringByKey(key, userSession.requireUser()).map {
            it ?: defaultValue
        }
    }

    override suspend fun setTelegramChatId(chatId: String) {
        val key = ApplicationPreferenceKey.TelegramChatId.key
        keyValueDataSource.setStringByKey(key, chatId, userSession.requireUser())
    }

    override suspend fun getTelegramChatId(): Flow<String> {
        val key = ApplicationPreferenceKey.TelegramChatId.key
        val defaultValue = ApplicationPreferenceKey.TelegramChatId.defaultValue

        return keyValueDataSource.getStringByKey(key, userSession.requireUser()).map {
            it ?: defaultValue
        }
    }

    override suspend fun setEmailEnabled(enabled: Boolean) {
        val key = ApplicationPreferenceKey.EmailEnabled.key
        keyValueDataSource.setBooleanByKey(key, enabled, userSession.requireUser())
    }

    override suspend fun isEmailEnabled(): Flow<Boolean> {
        val key = ApplicationPreferenceKey.EmailEnabled.key
        val defaultValue = ApplicationPreferenceKey.EmailEnabled.defaultValue

        return keyValueDataSource.getBooleanByKey(key, userSession.requireUser()).map {
            it ?: defaultValue
        }
    }

    override suspend fun setEmailSmtpHost(host: String) {
        val key = ApplicationPreferenceKey.EmailSmtpHost.key
        keyValueDataSource.setStringByKey(key, host, userSession.requireUser())
    }

    override suspend fun getEmailSmtpHost(): Flow<String> {
        val key = ApplicationPreferenceKey.EmailSmtpHost.key
        val defaultValue = ApplicationPreferenceKey.EmailSmtpHost.defaultValue

        return keyValueDataSource.getStringByKey(key, userSession.requireUser()).map {
            it ?: defaultValue
        }
    }

    override suspend fun setEmailSmtpPort(port: Int) {
        val key = ApplicationPreferenceKey.EmailSmtpPort.key
        keyValueDataSource.setIntByKey(key, port, userSession.requireUser())
    }

    override suspend fun getEmailSmtpPort(): Flow<Int> {
        val key = ApplicationPreferenceKey.EmailSmtpPort.key
        val defaultValue = ApplicationPreferenceKey.EmailSmtpPort.defaultValue

        return keyValueDataSource.getIntByKey(key, userSession.requireUser()).map {
            it ?: defaultValue
        }
    }

    override suspend fun setEmailUsername(username: String) {
        val key = ApplicationPreferenceKey.EmailUsername.key
        keyValueDataSource.setStringByKey(key, username, userSession.requireUser())
    }

    override suspend fun getEmailUsername(): Flow<String> {
        val key = ApplicationPreferenceKey.EmailUsername.key
        val defaultValue = ApplicationPreferenceKey.EmailUsername.defaultValue

        return keyValueDataSource.getStringByKey(key, userSession.requireUser()).map {
            it ?: defaultValue
        }
    }

    override suspend fun setEmailPassword(password: String) {
        val key = ApplicationPreferenceKey.EmailPassword.key
        keyValueDataSource.setStringByKey(key, password, userSession.requireUser())
    }

    override suspend fun getEmailPassword(): Flow<String> {
        val key = ApplicationPreferenceKey.EmailPassword.key
        val defaultValue = ApplicationPreferenceKey.EmailPassword.defaultValue

        return keyValueDataSource.getStringByKey(key, userSession.requireUser()).map {
            it ?: defaultValue
        }
    }

    override suspend fun setEmailFrom(from: String) {
        val key = ApplicationPreferenceKey.EmailFrom.key
        keyValueDataSource.setStringByKey(key, from, userSession.requireUser())
    }

    override suspend fun getEmailFrom(): Flow<String> {
        val key = ApplicationPreferenceKey.EmailFrom.key
        val defaultValue = ApplicationPreferenceKey.EmailFrom.defaultValue

        return keyValueDataSource.getStringByKey(key, userSession.requireUser()).map {
            it ?: defaultValue
        }
    }

    override suspend fun setEmailTo(to: String) {
        val key = ApplicationPreferenceKey.EmailTo.key
        keyValueDataSource.setStringByKey(key, to, userSession.requireUser())
    }

    override suspend fun getEmailTo(): Flow<String> {
        val key = ApplicationPreferenceKey.EmailTo.key
        val defaultValue = ApplicationPreferenceKey.EmailTo.defaultValue

        return keyValueDataSource.getStringByKey(key, userSession.requireUser()).map {
            it ?: defaultValue
        }
    }

    override suspend fun setEmailUseTls(useTls: Boolean) {
        val key = ApplicationPreferenceKey.EmailUseTls.key
        keyValueDataSource.setBooleanByKey(key, useTls, userSession.requireUser())
    }

    override suspend fun getEmailUseTls(): Flow<Boolean> {
        val key = ApplicationPreferenceKey.EmailUseTls.key
        val defaultValue = ApplicationPreferenceKey.EmailUseTls.defaultValue

        return keyValueDataSource.getBooleanByKey(key, userSession.requireUser()).map {
            it ?: defaultValue
        }
    }

    override suspend fun setNotificationCenterLastSeenAt(timestamp: Long) {
        val key = ApplicationPreferenceKey.NotificationCenterLastSeenAt.key
        keyValueDataSource.setLongByKey(key, timestamp, userSession.requireUser())
    }

    override suspend fun getNotificationCenterLastSeenAt(): Flow<Long> {
        val key = ApplicationPreferenceKey.NotificationCenterLastSeenAt.key
        val defaultValue = ApplicationPreferenceKey.NotificationCenterLastSeenAt.defaultValue
        return keyValueDataSource.getLongByKey(key, userSession.requireUser()).map {
            it ?: defaultValue
        }
    }
}

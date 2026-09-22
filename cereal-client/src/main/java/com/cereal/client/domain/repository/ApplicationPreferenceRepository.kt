package com.cereal.client.domain.repository

import com.cereal.client.domain.model.settings.ProxyHealthCheckInterval
import kotlinx.coroutines.flow.Flow

/**
 * App-wide preference toggles that are not tied to a more specific concern. Notification settings
 * live on [NotificationSettingsRepository] and vendor credentials on their own repositories.
 */
interface ApplicationPreferenceRepository {
    suspend fun setDiscordActivityStatusEnabled(enabled: Boolean)

    suspend fun isDiscordActivityStatusEnabled(): Flow<Boolean>

    suspend fun setDevelopmentScriptsEnabled(enabled: Boolean)

    suspend fun isDevelopmentScriptsEnabled(): Flow<Boolean>

    suspend fun setShowDebugLogs(enabled: Boolean)

    suspend fun isShowDebugLogs(): Flow<Boolean>

    suspend fun setProxyHealthCheckInterval(interval: ProxyHealthCheckInterval)

    suspend fun getProxyHealthCheckInterval(): Flow<ProxyHealthCheckInterval>
}

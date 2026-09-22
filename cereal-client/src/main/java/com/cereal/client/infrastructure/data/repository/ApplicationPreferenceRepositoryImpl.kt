package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.settings.ProxyHealthCheckInterval
import com.cereal.client.domain.repository.ApplicationPreferenceRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.KeyValueDataSource
import com.cereal.client.infrastructure.data.datasource.preference.ApplicationPreferenceKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ApplicationPreferenceRepositoryImpl(
    private val keyValueDataSource: KeyValueDataSource,
    private val userSession: UserSession,
) : ApplicationPreferenceRepository {
    override suspend fun setDiscordActivityStatusEnabled(enabled: Boolean) {
        val key = ApplicationPreferenceKey.DiscordActivityStatusEnabled.key
        keyValueDataSource.setBooleanByKey(key, enabled, userSession.requireUser())
    }

    override suspend fun isDiscordActivityStatusEnabled(): Flow<Boolean> {
        val key = ApplicationPreferenceKey.DiscordActivityStatusEnabled.key
        val defaultValue = ApplicationPreferenceKey.DiscordActivityStatusEnabled.defaultValue

        return keyValueDataSource
            .getBooleanByKey(key, userSession.requireUser())
            .map { it ?: defaultValue }
    }

    override suspend fun setDevelopmentScriptsEnabled(enabled: Boolean) {
        val key = ApplicationPreferenceKey.DevelopmentScriptsEnabled.key
        keyValueDataSource.setBooleanByKey(key, enabled, userSession.requireUser())
    }

    override suspend fun isDevelopmentScriptsEnabled(): Flow<Boolean> {
        val key = ApplicationPreferenceKey.DevelopmentScriptsEnabled.key
        val defaultValue = ApplicationPreferenceKey.DevelopmentScriptsEnabled.defaultValue

        return keyValueDataSource.getBooleanByKey(key, userSession.requireUser()).map {
            it ?: defaultValue
        }
    }

    override suspend fun setShowDebugLogs(enabled: Boolean) {
        val key = ApplicationPreferenceKey.ShowDebugLogs.key
        keyValueDataSource.setBooleanByKey(key, enabled, userSession.requireUser())
    }

    override suspend fun isShowDebugLogs(): Flow<Boolean> {
        val key = ApplicationPreferenceKey.ShowDebugLogs.key
        val defaultValue = ApplicationPreferenceKey.ShowDebugLogs.defaultValue

        return keyValueDataSource.getBooleanByKey(key, userSession.requireUser()).map {
            it ?: defaultValue
        }
    }

    override suspend fun setProxyHealthCheckInterval(interval: ProxyHealthCheckInterval) {
        val key = ApplicationPreferenceKey.ProxyHealthCheckInterval.key
        keyValueDataSource.setStringByKey(key, interval.storageValue, userSession.requireUser())
    }

    override suspend fun getProxyHealthCheckInterval(): Flow<ProxyHealthCheckInterval> {
        val key = ApplicationPreferenceKey.ProxyHealthCheckInterval.key
        return keyValueDataSource.getStringByKey(key, userSession.requireUser()).map {
            ProxyHealthCheckInterval.fromStorageValue(it)
        }
    }
}

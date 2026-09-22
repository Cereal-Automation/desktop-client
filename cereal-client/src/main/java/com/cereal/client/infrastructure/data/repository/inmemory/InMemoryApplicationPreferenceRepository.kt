package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.settings.ProxyHealthCheckInterval
import com.cereal.client.domain.repository.ApplicationPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [ApplicationPreferenceRepository] for screen and interactor tests. Every setting is
 * backed by a [MutableStateFlow] so getters return live values and setters update them.
 */
class InMemoryApplicationPreferenceRepository : ApplicationPreferenceRepository {
    private val developmentScriptsEnabled = MutableStateFlow(false)
    private val discordActivityStatusEnabled = MutableStateFlow(true)
    private val showDebugLogs = MutableStateFlow(false)
    private val proxyHealthCheckInterval = MutableStateFlow(ProxyHealthCheckInterval.Default)

    override suspend fun setDiscordActivityStatusEnabled(enabled: Boolean) {
        discordActivityStatusEnabled.value = enabled
    }

    override suspend fun isDiscordActivityStatusEnabled(): Flow<Boolean> = discordActivityStatusEnabled

    override suspend fun setDevelopmentScriptsEnabled(enabled: Boolean) {
        developmentScriptsEnabled.value = enabled
    }

    override suspend fun isDevelopmentScriptsEnabled(): Flow<Boolean> = developmentScriptsEnabled

    override suspend fun setShowDebugLogs(enabled: Boolean) {
        showDebugLogs.value = enabled
    }

    override suspend fun isShowDebugLogs(): Flow<Boolean> = showDebugLogs

    override suspend fun setProxyHealthCheckInterval(interval: ProxyHealthCheckInterval) {
        proxyHealthCheckInterval.value = interval
    }

    override suspend fun getProxyHealthCheckInterval(): Flow<ProxyHealthCheckInterval> = proxyHealthCheckInterval
}

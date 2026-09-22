package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.proxy.ProxyProviderConnector
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.repository.ProxyProviderConnectorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [ProxyProviderConnectorRepository] for the `mock` flavor and tests. Holds the connector
 * record in memory (no Room). Shared with [InMemoryProxyConnectionProvider] so connect/sync/disconnect
 * and reads observe the same state.
 */
class InMemoryProxyProviderConnectorRepository(
    initialConnector: ProxyProviderConnector? = null,
) : ProxyProviderConnectorRepository {
    private val mutex = Mutex()
    private val connectorFlow = MutableStateFlow(initialConnector)

    override suspend fun getConnectedProvider(provider: ProxyVendor): Flow<ProxyProviderConnector?> = connectorFlow.map { it?.takeIf { c -> c.provider == provider } }

    override suspend fun upsertConnector(connector: ProxyProviderConnector) {
        mutex.withLock { connectorFlow.value = connector }
    }

    override suspend fun deleteConnector(provider: ProxyVendor) {
        mutex.withLock {
            if (connectorFlow.value?.provider == provider) connectorFlow.value = null
        }
    }
}

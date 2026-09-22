package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.proxy.ProxyProviderConnector
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.repository.ProxyProviderConnectorRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.ProxyProviderConnectorDataSource
import kotlinx.coroutines.flow.Flow

/**
 * Default [ProxyProviderConnectorRepository]. Persists and reads the provider-keyed connector record
 * for the current user via [ProxyProviderConnectorDataSource].
 */
class ProxyProviderConnectorRepositoryImpl(
    private val connectorDataSource: ProxyProviderConnectorDataSource,
    private val userSession: UserSession,
) : ProxyProviderConnectorRepository {
    override suspend fun getConnectedProvider(provider: ProxyVendor): Flow<ProxyProviderConnector?> = connectorDataSource.observeConnector(userSession.requireUser(), provider)

    override suspend fun upsertConnector(connector: ProxyProviderConnector) {
        connectorDataSource.upsertConnector(userSession.requireUser(), connector)
    }

    override suspend fun deleteConnector(provider: ProxyVendor) {
        connectorDataSource.deleteConnector(userSession.requireUser(), provider)
    }
}

package com.cereal.client.domain.repository

import com.cereal.client.domain.model.proxy.ProxyProviderConnector
import com.cereal.client.domain.model.proxy.ProxyVendor
import kotlinx.coroutines.flow.Flow

/**
 * Owns the persisted proxy-provider connector records — which provider is connected, its resolved
 * sub-user, traffic figures and last-sync timestamp. Connecting to and syncing from the proxy vendor
 * itself is a provider concern — see [com.cereal.client.domain.provider.ProxyConnectionProvider],
 * which delegates connector persistence here.
 */
interface ProxyProviderConnectorRepository {
    /** Emits the connector record for [provider], or `null` when not connected. */
    suspend fun getConnectedProvider(provider: ProxyVendor): Flow<ProxyProviderConnector?>

    /** Stores (or overwrites) the connector record for the current user. */
    suspend fun upsertConnector(connector: ProxyProviderConnector)

    /** Removes the connector record for [provider]. Existing proxy groups are left intact. */
    suspend fun deleteConnector(provider: ProxyVendor)
}

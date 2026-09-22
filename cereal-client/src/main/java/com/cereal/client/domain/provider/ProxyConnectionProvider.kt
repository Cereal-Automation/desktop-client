package com.cereal.client.domain.provider

import com.cereal.client.domain.model.proxy.ProxyProviderConnector
import com.cereal.client.domain.model.proxy.ProxySyncConfig
import com.cereal.client.domain.model.proxy.ProxySyncResult
import com.cereal.client.domain.model.proxy.ProxyVendor

/**
 * Adapter to a proxy vendor's API (currently MarsProxies): validates credentials, resolves a
 * sub-user, and generates/syncs live proxies. This is a *provider* (an outbound integration),
 * distinct from [com.cereal.client.domain.repository.ProxyProviderConnectorRepository], which owns
 * the persisted connector record. Connect/disconnect operations delegate connector persistence to
 * that repository.
 */
interface ProxyConnectionProvider {
    /**
     * Connects (or re-connects, overwriting any existing credential) to [provider] using [token].
     *
     * Validates the token against the provider account endpoint, resolves the sub-user with the most
     * available traffic, stores the token as a sensitive credential, and upserts the connector record.
     *
     * @throws com.cereal.client.domain.model.exception.InvalidProxyProviderTokenException if the token is rejected.
     * @throws com.cereal.client.domain.model.exception.NoProxyProviderSubUserException if the account has no sub-users.
     * @throws com.cereal.client.domain.model.exception.ProxyProviderConnectivityException on network failure.
     */
    suspend fun connect(
        provider: ProxyVendor,
        token: String,
    ): ProxyProviderConnector

    /**
     * Syncs live proxies from [provider] into a proxy group, per [config].
     *
     * @throws com.cereal.client.domain.model.exception.ProxyProviderNotConnectedException if not connected.
     * @throws com.cereal.client.domain.model.exception.ProxySyncFailedException if generation fails.
     */
    suspend fun syncProxies(
        provider: ProxyVendor,
        config: ProxySyncConfig,
    ): ProxySyncResult

    /**
     * Disconnects [provider]: clears the stored credential and the connector record.
     * Existing proxy groups are left intact.
     */
    suspend fun disconnect(provider: ProxyVendor)
}

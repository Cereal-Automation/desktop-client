package com.cereal.client.application.interactor.proxy.provider

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.proxy.ProxySyncConfig
import com.cereal.client.domain.model.proxy.ProxySyncResult
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.provider.ProxyConnectionProvider

/**
 * Syncs live proxies from a connected provider into a proxy group per the supplied config.
 *
 * Surfaces [com.cereal.client.domain.model.exception.ProxyProviderNotConnectedException] and
 * [com.cereal.client.domain.model.exception.ProxySyncFailedException] from the provider.
 */
class SyncProxiesInteractor(
    private val proxyConnectionProvider: ProxyConnectionProvider,
) : Interactor<ProxySyncResult, SyncProxiesInteractor.Params>() {
    override suspend fun run(params: Params): ProxySyncResult = proxyConnectionProvider.syncProxies(params.provider, params.config)

    data class Params(
        val provider: ProxyVendor,
        val config: ProxySyncConfig,
    )
}

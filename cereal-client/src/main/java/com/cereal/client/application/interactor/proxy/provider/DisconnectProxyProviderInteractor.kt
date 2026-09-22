package com.cereal.client.application.interactor.proxy.provider

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.provider.ProxyConnectionProvider

/**
 * Disconnects a proxy provider: clears the stored credential and the connector record.
 * Existing proxy groups are left intact.
 */
class DisconnectProxyProviderInteractor(
    private val proxyConnectionProvider: ProxyConnectionProvider,
) : Interactor<Unit, DisconnectProxyProviderInteractor.Params>() {
    override suspend fun run(params: Params) {
        proxyConnectionProvider.disconnect(params.provider)
    }

    data class Params(
        val provider: ProxyVendor,
    )
}

package com.cereal.client.application.interactor.proxy.provider

import com.cereal.client.application.Interactor
import com.cereal.client.application.SensitiveParams
import com.cereal.client.domain.model.proxy.ProxyProviderConnector
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.provider.ProxyConnectionProvider

/**
 * Connects to a proxy provider with a freshly-entered token. Also serves replace-token (the repository
 * upserts the connector record), so re-running validation and overwriting the credential is the same call.
 *
 * Surfaces [com.cereal.client.domain.model.exception.InvalidProxyProviderTokenException],
 * [com.cereal.client.domain.model.exception.NoProxyProviderSubUserException], and
 * [com.cereal.client.domain.model.exception.ProxyProviderConnectivityException] from the provider.
 */
class ConnectProxyProviderInteractor(
    private val proxyConnectionProvider: ProxyConnectionProvider,
) : Interactor<ProxyProviderConnector, ConnectProxyProviderInteractor.Params>() {
    override suspend fun run(params: Params): ProxyProviderConnector = proxyConnectionProvider.connect(params.provider, params.token)

    data class Params(
        val provider: ProxyVendor,
        val token: String,
    ) : SensitiveParams
}

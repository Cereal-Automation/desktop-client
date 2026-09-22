package com.cereal.client.application.interactor.proxy.provider

import com.cereal.client.application.FlowInteractor
import com.cereal.client.domain.model.proxy.ProxyProviderConnector
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.repository.ProxyProviderConnectorRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

/** Observes the connector record for a provider, emitting `null` when not connected. */
@OptIn(FlowPreview::class)
class GetConnectedProxyProviderInteractor(
    private val proxyProviderConnectorRepository: ProxyProviderConnectorRepository,
) : FlowInteractor<ProxyProviderConnector?, GetConnectedProxyProviderInteractor.Params>() {
    override suspend fun run(params: Params): Flow<ProxyProviderConnector?> = proxyProviderConnectorRepository.getConnectedProvider(params.provider)

    data class Params(
        val provider: ProxyVendor,
    )
}

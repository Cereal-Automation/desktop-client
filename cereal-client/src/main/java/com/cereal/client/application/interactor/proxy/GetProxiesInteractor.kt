package com.cereal.client.application.interactor.proxy

import com.cereal.client.application.FlowInteractor
import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.repository.ProxyRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@OptIn(FlowPreview::class)
class GetProxiesInteractor(
    private val proxyRepository: ProxyRepository,
) : FlowInteractor<List<Proxy>, GetProxiesInteractor.Params>() {
    override suspend fun run(params: Params): Flow<List<Proxy>> = proxyRepository.getProxiesFlow(params.proxyGroup)

    data class Params(
        val proxyGroup: ProxyGroup,
    )
}

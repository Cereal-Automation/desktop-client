package com.cereal.client.application.interactor.proxy

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.repository.ProxyRepository

class DeleteFailedProxiesInGroupInteractor(
    private val proxyRepository: ProxyRepository,
) : Interactor<Int, DeleteFailedProxiesInGroupInteractor.Params>() {
    /** Returns the number of proxies that were deleted. */
    override suspend fun run(params: Params): Int = proxyRepository.deleteFailedProxiesFromGroup(params.proxyGroup)

    data class Params(
        val proxyGroup: ProxyGroup,
    )
}

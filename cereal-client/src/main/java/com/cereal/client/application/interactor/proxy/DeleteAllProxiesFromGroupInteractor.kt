package com.cereal.client.application.interactor.proxy

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.repository.ProxyRepository

class DeleteAllProxiesFromGroupInteractor(
    private val proxyRepository: ProxyRepository,
) : Interactor<Unit, DeleteAllProxiesFromGroupInteractor.Params>() {
    override suspend fun run(params: Params) {
        proxyRepository.deleteProxiesFromGroup(params.proxyGroup)
    }

    data class Params(
        val proxyGroup: ProxyGroup,
    )
}

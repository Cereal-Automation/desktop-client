package com.cereal.client.application.interactor.proxy

import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.ProxyRepository

class UpdateProxyGroupInteractor(
    private val proxyRepository: ProxyRepository,
) : Interactor<Unit, UpdateProxyGroupInteractor.Params>() {
    override suspend fun run(params: Params) {
        proxyRepository.updateProxyGroup(params.groupId, params.name)
    }

    data class Params(
        val groupId: String,
        val name: String,
    )
}

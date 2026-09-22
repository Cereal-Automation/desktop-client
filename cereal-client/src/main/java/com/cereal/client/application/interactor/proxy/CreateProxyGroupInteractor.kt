package com.cereal.client.application.interactor.proxy

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.repository.ProxyRepository
import java.util.UUID

class CreateProxyGroupInteractor(
    private val proxyRepository: ProxyRepository,
) : Interactor<ProxyGroup, CreateProxyGroupInteractor.Params>() {
    override suspend fun run(params: Params): ProxyGroup {
        val proxyGroup =
            ProxyGroup(
                id = UUID.randomUUID().toString(),
                name = params.name,
                items = emptySequence(),
                numberOfItems = 0,
            )
        proxyRepository.createProxyGroup(proxyGroup)

        return proxyGroup
    }

    data class Params(
        val name: String,
    )
}

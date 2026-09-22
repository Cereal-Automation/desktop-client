package com.cereal.client.application.interactor.proxy

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.repository.ProxyRepository

class DeleteProxyInteractor(
    private val proxyRepository: ProxyRepository,
) : Interactor<Unit, DeleteProxyInteractor.Params>() {
    override suspend fun run(params: Params) {
        proxyRepository.deleteProxy(params.proxy)
    }

    data class Params(
        val proxy: Proxy,
    )
}

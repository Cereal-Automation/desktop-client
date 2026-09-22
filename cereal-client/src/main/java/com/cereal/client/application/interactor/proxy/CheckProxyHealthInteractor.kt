package com.cereal.client.application.interactor.proxy

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyHealth
import com.cereal.client.domain.repository.ProxyRepository
import com.cereal.client.domain.service.ProxyHealthChecker

class CheckProxyHealthInteractor(
    private val proxyHealthChecker: ProxyHealthChecker,
    private val proxyRepository: ProxyRepository,
) : Interactor<ProxyHealth, CheckProxyHealthInteractor.Params>() {
    override suspend fun run(params: Params): ProxyHealth {
        val health = proxyHealthChecker.check(params.proxy)
        proxyRepository.updateProxyHealth(params.proxy.id, health)
        return health
    }

    data class Params(
        val proxy: Proxy,
    )
}

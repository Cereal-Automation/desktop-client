package com.cereal.client.application.interactor.proxy

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.repository.ProxyRepository
import java.security.InvalidParameterException

class DeleteProxyGroupInteractor(
    private val proxyRepository: ProxyRepository,
) : Interactor<Unit, DeleteProxyGroupInteractor.Params>() {
    override suspend fun run(params: Params) {
        if (params.proxyGroup == null) {
            throw InvalidParameterException("Missing ProxyGroup parameter.")
        }
        proxyRepository.deleteProxyGroup(params.proxyGroup)
    }

    data class Params(
        val proxyGroup: ProxyGroup?,
    )
}

package com.cereal.client.application.interactor.files

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.repository.ProxyRepository
import java.io.File

class AddProxiesFromFileToGroupInteractor(
    private val proxyRepository: ProxyRepository,
) : Interactor<AddProxiesFromFileToGroupInteractor.Result, AddProxiesFromFileToGroupInteractor.Params>() {
    override suspend fun run(params: Params): Result {
        val proxies = proxyRepository.readFromFile(params.file)

        proxies.forEach {
            proxyRepository.createOrUpdateProxy(it, params.group)
        }

        return Result(params.group.copy(numberOfItems = proxies.size))
    }

    data class Params(
        val file: File,
        val group: ProxyGroup,
    )

    data class Result(
        val group: ProxyGroup,
    )
}

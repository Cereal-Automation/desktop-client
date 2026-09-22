package com.cereal.client.application.interactor.files

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.repository.ProxyRepository
import java.io.File
import java.util.UUID

class ReadProxyFileInteractor(
    private val proxyRepository: ProxyRepository,
) : Interactor<ReadProxyFileInteractor.Result, ReadProxyFileInteractor.Params>() {
    override suspend fun run(params: Params): Result {
        val proxies = proxyRepository.readFromFile(params.file)

        val group =
            ProxyGroup(
                id = UUID.randomUUID().toString(),
                name = createGroupName(params.manifest),
                items = proxies.asSequence(),
                numberOfItems = proxies.size,
            )

        proxyRepository.createProxyGroup(group)

        return Result(group)
    }

    data class Params(
        val file: File,
        val manifest: Manifest,
    )

    data class Result(
        val group: ProxyGroup,
    )
}

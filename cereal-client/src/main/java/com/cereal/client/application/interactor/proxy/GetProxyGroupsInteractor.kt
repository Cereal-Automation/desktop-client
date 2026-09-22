package com.cereal.client.application.interactor.proxy

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.repository.ProxyRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@OptIn(FlowPreview::class)
class GetProxyGroupsInteractor(
    private val proxyRepository: ProxyRepository,
) : FlowInteractor<List<ProxyGroup>, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<List<ProxyGroup>> = proxyRepository.getProxyGroups()
}

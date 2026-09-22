package com.cereal.client.application.interactor.proxy

import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealth
import com.cereal.client.domain.repository.ProxyRepository
import com.cereal.client.domain.service.ProxyHealthChecker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

class CheckProxiesInGroupInteractor(
    private val proxyRepository: ProxyRepository,
    private val proxyHealthChecker: ProxyHealthChecker,
) {
    data class Params(
        val group: ProxyGroup,
        val maxConcurrent: Int = DEFAULT_MAX_CONCURRENT,
    )

    data class Result(
        val proxyId: UUID,
        val health: ProxyHealth,
    )

    fun run(params: Params): Flow<Result> =
        channelFlow {
            val proxies = proxyRepository.getProxiesFromGroup(params.group.id)
            val semaphore = Semaphore(params.maxConcurrent.coerceAtLeast(1))
            coroutineScope {
                proxies.forEach { proxy ->
                    launch {
                        semaphore.withPermit {
                            val result = checkAndPersist(proxy)
                            send(result)
                        }
                    }
                }
            }
        }

    private suspend fun checkAndPersist(proxy: Proxy): Result {
        val health = proxyHealthChecker.check(proxy)
        proxyRepository.updateProxyHealth(proxy.id, health)
        return Result(proxy.id, health)
    }

    companion object {
        const val DEFAULT_MAX_CONCURRENT = 8
    }
}

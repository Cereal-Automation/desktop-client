package com.cereal.client.application.task

import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealthStatus
import com.cereal.client.domain.repository.ProxyRepository

class ProxiesRandomizerProvider(
    private val proxyRepository: ProxyRepository,
) {
    private val proxiesRandomizers = mutableMapOf<ProxyGroup, ProxiesRandomizer>()

    suspend fun get(proxyGroup: ProxyGroup): ProxiesRandomizer =
        proxiesRandomizers.getOrPut(proxyGroup) {
            val proxies =
                proxyRepository
                    .getProxiesFromGroup(proxyGroup.id)
                    .filter { proxy -> proxy.health.status != ProxyHealthStatus.FAILED }
            ProxiesRandomizer(proxies)
        }
}

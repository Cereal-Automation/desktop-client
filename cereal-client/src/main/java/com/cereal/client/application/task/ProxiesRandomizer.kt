package com.cereal.client.application.task

import com.cereal.client.domain.model.proxy.Proxy

class ProxiesRandomizer(
    proxies: List<Proxy>,
) {
    private val proxyUsage = proxies.associateWith { 0 }.toMutableMap()

    fun getAndIncrementLeastUsedProxy(): Proxy = proxyUsage.getAndIncrementLeastUsedProxy()

    fun increment(proxy: Proxy) {
        proxyUsage[proxy] = (proxyUsage[proxy] ?: 0) + 1
    }

    private fun MutableMap<Proxy, Int>.getAndIncrementLeastUsedProxy(): Proxy {
        val entry =
            this.minByOrNull { it.value }
                ?: error("No proxies available to assign — every proxy in the group is marked as failed.")
        increment(entry.key)
        return entry.key
    }
}

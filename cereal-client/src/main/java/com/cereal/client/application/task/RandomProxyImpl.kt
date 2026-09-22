package com.cereal.client.application.task

import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.sdk.models.proxy.Proxy
import com.cereal.sdk.models.proxy.RandomProxy

class RandomProxyImpl(
    private val proxyGroup: ProxyGroup,
    private val proxiesRandomizerProvider: ProxiesRandomizerProvider,
) : RandomProxy {
    override suspend fun invoke(): Proxy {
        val proxiesRandomizer = proxiesRandomizerProvider.get(proxyGroup)
        return proxiesRandomizer.getAndIncrementLeastUsedProxy().toComponentProxy()
    }
}

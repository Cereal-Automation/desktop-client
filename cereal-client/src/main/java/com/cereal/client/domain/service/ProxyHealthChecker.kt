package com.cereal.client.domain.service

import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyHealth

interface ProxyHealthChecker {
    suspend fun check(proxy: Proxy): ProxyHealth
}

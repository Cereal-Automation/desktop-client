package com.cereal.client.domain.repository

import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealth
import com.cereal.client.domain.model.proxy.ProxyVendor
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

interface ProxyRepository {
    suspend fun createProxyGroup(proxyGroup: ProxyGroup)

    suspend fun updateProxyGroup(
        id: String,
        name: String,
    )

    suspend fun deleteProxyGroup(proxyGroup: ProxyGroup)

    /** Stamps [groupId] with the proxy [provider] it was synced from and a human-readable [geoLabel]. */
    suspend fun stampProxyGroupProvider(
        groupId: String,
        provider: ProxyVendor,
        geoLabel: String?,
    )

    suspend fun getProxyGroups(): Flow<List<ProxyGroup>>

    suspend fun getProxiesFlow(proxyGroup: ProxyGroup): Flow<List<Proxy>>

    suspend fun createOrUpdateProxy(
        proxy: Proxy,
        group: ProxyGroup,
    )

    suspend fun deleteProxy(proxy: Proxy)

    suspend fun deleteProxiesFromGroup(proxyGroup: ProxyGroup)

    /** Deletes all proxies in [proxyGroup] whose health status is FAILED. Returns the number of rows deleted. */
    suspend fun deleteFailedProxiesFromGroup(proxyGroup: ProxyGroup): Int

    suspend fun getProxiesFromGroup(proxyGroupId: String): List<Proxy>

    suspend fun getProxiesInGroupCount(proxyGroupId: String): Long

    suspend fun readFromFile(file: File): List<Proxy>

    suspend fun updateProxyHealth(
        proxyId: UUID,
        health: ProxyHealth,
    )

    @OptIn(ExperimentalTime::class)
    suspend fun getStaleProxies(olderThan: Instant): List<Proxy>
}

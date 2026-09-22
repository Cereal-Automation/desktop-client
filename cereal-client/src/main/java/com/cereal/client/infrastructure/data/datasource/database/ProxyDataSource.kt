package com.cereal.client.infrastructure.data.datasource.database

import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealth
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.model.user.User
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

interface ProxyDataSource {
    fun getProxyGroups(user: User): Flow<List<ProxyGroup>>

    suspend fun createProxyGroup(
        user: User,
        proxyGroup: ProxyGroup,
    )

    suspend fun updateProxyGroup(
        user: User,
        id: String,
        name: String,
    )

    suspend fun deleteProxyGroup(
        user: User,
        proxyGroupId: String,
    )

    suspend fun stampProxyGroupProvider(
        user: User,
        proxyGroupId: String,
        provider: ProxyVendor,
        geoLabel: String?,
    )

    suspend fun getProxiesFromGroup(
        user: User,
        proxyGroupId: String,
    ): List<Proxy>

    suspend fun getProxiesInGroupCount(
        user: User,
        proxyGroupId: String,
    ): Long

    fun getProxiesFlow(
        user: User,
        proxyGroupId: String,
    ): Flow<List<Proxy>>

    suspend fun updateOrCreateProxy(
        user: User,
        proxy: Proxy,
        groupId: String,
    )

    suspend fun deleteProxy(
        user: User,
        proxyId: UUID,
    )

    suspend fun deleteAllProxiesFromGroup(
        user: User,
        proxyGroupId: String,
    )

    suspend fun deleteFailedProxiesFromGroup(
        user: User,
        proxyGroupId: String,
    ): Int

    suspend fun getProxy(
        user: User,
        id: String,
    ): Proxy?

    suspend fun getProxyGroup(
        user: User,
        id: String,
    ): ProxyGroup?

    suspend fun updateProxyHealth(
        user: User,
        proxyId: UUID,
        health: ProxyHealth,
    )

    @OptIn(ExperimentalTime::class)
    suspend fun getStaleProxies(
        user: User,
        olderThan: Instant,
    ): List<Proxy>
}

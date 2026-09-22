package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealth
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.repository.ProxyRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.ProxyDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemProxyTemplateDataSource
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ProxyRepositoryImpl(
    private val proxyDataSource: ProxyDataSource,
    private val userSession: UserSession,
    private val fileSystemProxyTemplateDataSource: FileSystemProxyTemplateDataSource,
) : ProxyRepository {
    override suspend fun createProxyGroup(proxyGroup: ProxyGroup) = proxyDataSource.createProxyGroup(userSession.requireUser(), proxyGroup)

    override suspend fun updateProxyGroup(
        id: String,
        name: String,
    ) = proxyDataSource.updateProxyGroup(userSession.requireUser(), id, name)

    override suspend fun deleteProxyGroup(proxyGroup: ProxyGroup) = proxyDataSource.deleteProxyGroup(userSession.requireUser(), proxyGroup.id)

    override suspend fun stampProxyGroupProvider(
        groupId: String,
        provider: ProxyVendor,
        geoLabel: String?,
    ) = proxyDataSource.stampProxyGroupProvider(userSession.requireUser(), groupId, provider, geoLabel)

    override suspend fun getProxyGroups(): Flow<List<ProxyGroup>> = proxyDataSource.getProxyGroups(userSession.requireUser())

    override suspend fun getProxiesFlow(proxyGroup: ProxyGroup): Flow<List<Proxy>> = proxyDataSource.getProxiesFlow(userSession.requireUser(), proxyGroup.id)

    override suspend fun createOrUpdateProxy(
        proxy: Proxy,
        group: ProxyGroup,
    ) = proxyDataSource.updateOrCreateProxy(userSession.requireUser(), proxy, group.id)

    override suspend fun deleteProxy(proxy: Proxy) = proxyDataSource.deleteProxy(userSession.requireUser(), proxy.id)

    override suspend fun deleteProxiesFromGroup(proxyGroup: ProxyGroup) {
        proxyDataSource.deleteAllProxiesFromGroup(userSession.requireUser(), proxyGroup.id)
    }

    override suspend fun deleteFailedProxiesFromGroup(proxyGroup: ProxyGroup): Int = proxyDataSource.deleteFailedProxiesFromGroup(userSession.requireUser(), proxyGroup.id)

    override suspend fun getProxiesFromGroup(proxyGroupId: String): List<Proxy> = proxyDataSource.getProxiesFromGroup(userSession.requireUser(), proxyGroupId)

    override suspend fun getProxiesInGroupCount(proxyGroupId: String): Long = proxyDataSource.getProxiesInGroupCount(userSession.requireUser(), proxyGroupId)

    override suspend fun readFromFile(file: File): List<Proxy> =
        fileSystemProxyTemplateDataSource.readFromTemplate(file).map {
            Proxy(
                id = UUID.randomUUID(),
                address = it.address,
                port = it.port,
                username = it.username,
                password = it.password,
            )
        }

    override suspend fun updateProxyHealth(
        proxyId: UUID,
        health: ProxyHealth,
    ) = proxyDataSource.updateProxyHealth(userSession.requireUser(), proxyId, health)

    @OptIn(ExperimentalTime::class)
    override suspend fun getStaleProxies(olderThan: Instant): List<Proxy> = proxyDataSource.getStaleProxies(userSession.requireUser(), olderThan)
}

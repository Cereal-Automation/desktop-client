package com.cereal.client.infrastructure.data.datasource.database.room

import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealth
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.ProxyDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.ProxyMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Room implementation of ProxyDataSource
 */
class RoomProxyDataSource(
    private val roomDatabases: RoomDatabases,
) : ProxyDataSource {
    private val mapper = ProxyMapper()

    override fun getProxyGroups(user: User): Flow<List<ProxyGroup>> {
        val dao = roomDatabases.getUserDatabase(user).proxyDao()
        return dao.getProxyGroupsWithCountsFlow().map { groupEntities ->
            groupEntities.map { groupEntity ->
                mapper.toDomain(groupEntity)
            }
        }
    }

    override suspend fun createProxyGroup(
        user: User,
        proxyGroup: ProxyGroup,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            val dao = userDatabase.proxyDao()
            val groupEntity = mapper.createProxyGroupEntity(proxyGroup)
            dao.insertProxyGroup(groupEntity)

            // Insert all proxies in the group
            val proxyEntities =
                proxyGroup.items
                    .map { proxy ->
                        mapper.createProxyEntity(proxy, proxyGroup.id)
                    }.toList()

            if (proxyEntities.isNotEmpty()) {
                dao.insertProxies(proxyEntities)
            }
        }
    }

    override suspend fun updateProxyGroup(
        user: User,
        id: String,
        name: String,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            val dao = userDatabase.proxyDao()
            val existingGroup = dao.getProxyGroupById(id)
            if (existingGroup != null) {
                val updatedGroup = mapper.updateProxyGroupEntity(existingGroup, name)
                dao.updateProxyGroup(updatedGroup)
            }
        }
    }

    override suspend fun deleteProxyGroup(
        user: User,
        proxyGroupId: String,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            val dao = userDatabase.proxyDao()
            val groupEntity = dao.getProxyGroupById(proxyGroupId)
            if (groupEntity != null) {
                // Delete all proxies in the group first (handled by foreign key cascade)
                dao.deleteProxyGroup(groupEntity)
            }
        }
    }

    override suspend fun stampProxyGroupProvider(
        user: User,
        proxyGroupId: String,
        provider: ProxyVendor,
        geoLabel: String?,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            val dao = userDatabase.proxyDao()
            val existingGroup = dao.getProxyGroupById(proxyGroupId)
            if (existingGroup != null) {
                dao.updateProxyGroup(mapper.stampProviderEntity(existingGroup, provider, geoLabel))
            }
        }
    }

    override suspend fun getProxyGroup(
        user: User,
        id: String,
    ): ProxyGroup? {
        val dao = roomDatabases.getUserDatabase(user).proxyDao()
        val groupEntity = dao.getProxyGroupById(id)
        return if (groupEntity != null) {
            val proxies = dao.getProxiesByGroupId(id)
            mapper.toDomain(groupEntity, proxies)
        } else {
            null
        }
    }

    override suspend fun getProxiesFromGroup(
        user: User,
        proxyGroupId: String,
    ): List<Proxy> {
        val dao = roomDatabases.getUserDatabase(user).proxyDao()
        return dao.getProxiesByGroupId(proxyGroupId).map { mapper.toDomain(it) }
    }

    override suspend fun getProxiesInGroupCount(
        user: User,
        proxyGroupId: String,
    ): Long {
        val dao = roomDatabases.getUserDatabase(user).proxyDao()
        return dao.getProxiesCountByGroupId(proxyGroupId)
    }

    override fun getProxiesFlow(
        user: User,
        proxyGroupId: String,
    ): Flow<List<Proxy>> {
        val dao = roomDatabases.getUserDatabase(user).proxyDao()
        return dao.getProxiesByGroupIdFlow(proxyGroupId).map { entities ->
            entities.map { mapper.toDomain(it) }
        }
    }

    override suspend fun getProxy(
        user: User,
        id: String,
    ): Proxy? {
        val dao = roomDatabases.getUserDatabase(user).proxyDao()
        return dao.getProxyById(id)?.let { mapper.toDomain(it) }
    }

    override suspend fun updateOrCreateProxy(
        user: User,
        proxy: Proxy,
        groupId: String,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            val dao = userDatabase.proxyDao()
            val existingProxy = dao.getProxyById(proxy.id.toString())

            if (existingProxy != null) {
                // Update existing proxy
                val updatedProxy = mapper.updateProxyEntity(existingProxy, proxy)
                dao.updateProxy(updatedProxy)
            } else {
                // Create new proxy
                val newProxy = mapper.createProxyEntity(proxy, groupId)
                dao.insertProxy(newProxy)
            }
        }
    }

    override suspend fun deleteProxy(
        user: User,
        proxyId: UUID,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            val dao = userDatabase.proxyDao()
            dao.deleteProxyById(proxyId.toString())
        }
    }

    override suspend fun deleteAllProxiesFromGroup(
        user: User,
        proxyGroupId: String,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            val dao = userDatabase.proxyDao()
            dao.deleteAllProxiesByGroupId(proxyGroupId)
        }
    }

    override suspend fun deleteFailedProxiesFromGroup(
        user: User,
        proxyGroupId: String,
    ): Int {
        val userDatabase = roomDatabases.getUserDatabase(user)
        return userDatabase.immediateWriteTransaction {
            val dao = userDatabase.proxyDao()
            dao.deleteProxiesByGroupIdAndStatus(
                groupId = proxyGroupId,
                status = com.cereal.client.domain.model.proxy.ProxyHealthStatus.FAILED.name,
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun updateProxyHealth(
        user: User,
        proxyId: UUID,
        health: ProxyHealth,
    ) {
        val dao = roomDatabases.getUserDatabase(user).proxyDao()
        dao.updateProxyHealth(
            id = proxyId.toString(),
            status = health.status.name,
            lastCheckedAt = health.lastCheckedAt?.toEpochMilliseconds() ?: 0L,
            latencyMs = health.latencyMs,
            lastError = health.lastError,
        )
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun getStaleProxies(
        user: User,
        olderThan: Instant,
    ): List<Proxy> {
        val dao = roomDatabases.getUserDatabase(user).proxyDao()
        return dao.getStaleProxies(olderThan.toEpochMilliseconds()).map { mapper.toDomain(it) }
    }
}

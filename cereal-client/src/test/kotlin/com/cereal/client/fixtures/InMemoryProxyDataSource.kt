package com.cereal.client.fixtures

import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealth
import com.cereal.client.domain.model.proxy.ProxyHealthStatus
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.ProxyDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * In-memory fake of [ProxyDataSource].
 *
 * Groups and proxies are stored in [MutableStateFlow] backed maps keyed by user id (and, for
 * proxies, by group id), so that flow getters observe writes. Returned [ProxyGroup]s are
 * recomputed from the current proxies so [ProxyGroup.numberOfItems] and [ProxyGroup.items] stay
 * consistent with the stored proxies.
 */
@OptIn(ExperimentalTime::class)
class InMemoryProxyDataSource : ProxyDataSource {
    // user id -> (group id -> group name)
    private val groups = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())

    // user id -> (group id -> (proxy id -> proxy))
    private val proxies = MutableStateFlow<Map<String, Map<String, Map<UUID, Proxy>>>>(emptyMap())

    private fun proxiesIn(
        userId: String,
        groupId: String,
    ): Map<UUID, Proxy> = proxies.value[userId]?.get(groupId).orEmpty()

    private fun buildGroup(
        userId: String,
        groupId: String,
        name: String,
    ): ProxyGroup {
        val groupProxies = proxiesIn(userId, groupId).values.toList()
        return ProxyGroup(
            id = groupId,
            name = name,
            numberOfItems = groupProxies.size,
            items = groupProxies.asSequence(),
        )
    }

    override fun getProxyGroups(user: User): Flow<List<ProxyGroup>> =
        groups.map { allGroups ->
            allGroups[user.id].orEmpty().map { (groupId, name) ->
                buildGroup(user.id, groupId, name)
            }
        }

    override suspend fun createProxyGroup(
        user: User,
        proxyGroup: ProxyGroup,
    ) {
        val userGroups = groups.value[user.id].orEmpty()
        groups.value = groups.value + (user.id to (userGroups + (proxyGroup.id to proxyGroup.name)))
        proxyGroup.items.forEach { proxy ->
            updateOrCreateProxy(user, proxy, proxyGroup.id)
        }
    }

    override suspend fun updateProxyGroup(
        user: User,
        id: String,
        name: String,
    ) {
        val userGroups = groups.value[user.id].orEmpty()
        if (id in userGroups) {
            groups.value = groups.value + (user.id to (userGroups + (id to name)))
        }
    }

    override suspend fun deleteProxyGroup(
        user: User,
        proxyGroupId: String,
    ) {
        val userGroups = groups.value[user.id].orEmpty()
        groups.value = groups.value + (user.id to (userGroups - proxyGroupId))
        val userProxies = proxies.value[user.id].orEmpty()
        proxies.value = proxies.value + (user.id to (userProxies - proxyGroupId))
    }

    override suspend fun stampProxyGroupProvider(
        user: User,
        proxyGroupId: String,
        provider: com.cereal.client.domain.model.proxy.ProxyVendor,
        geoLabel: String?,
    ) {
        // This fake stores only group names; provider/geo metadata is not asserted by its callers.
    }

    override suspend fun getProxiesFromGroup(
        user: User,
        proxyGroupId: String,
    ): List<Proxy> = proxiesIn(user.id, proxyGroupId).values.toList()

    override suspend fun getProxiesInGroupCount(
        user: User,
        proxyGroupId: String,
    ): Long = proxiesIn(user.id, proxyGroupId).size.toLong()

    override fun getProxiesFlow(
        user: User,
        proxyGroupId: String,
    ): Flow<List<Proxy>> =
        proxies.map { all ->
            all[user.id]
                ?.get(proxyGroupId)
                ?.values
                ?.toList()
                .orEmpty()
        }

    override suspend fun updateOrCreateProxy(
        user: User,
        proxy: Proxy,
        groupId: String,
    ) {
        val userProxies = proxies.value[user.id].orEmpty()
        val groupProxies = userProxies[groupId].orEmpty()
        val updatedGroup = groupProxies + (proxy.id to proxy)
        proxies.value = proxies.value + (user.id to (userProxies + (groupId to updatedGroup)))
    }

    override suspend fun deleteProxy(
        user: User,
        proxyId: UUID,
    ) {
        val userProxies = proxies.value[user.id].orEmpty()
        val updated =
            userProxies.mapValues { (_, groupProxies) ->
                groupProxies - proxyId
            }
        proxies.value = proxies.value + (user.id to updated)
    }

    override suspend fun deleteAllProxiesFromGroup(
        user: User,
        proxyGroupId: String,
    ) {
        val userProxies = proxies.value[user.id].orEmpty()
        proxies.value = proxies.value + (user.id to (userProxies + (proxyGroupId to emptyMap())))
    }

    override suspend fun deleteFailedProxiesFromGroup(
        user: User,
        proxyGroupId: String,
    ): Int {
        val groupProxies = proxiesIn(user.id, proxyGroupId)
        val failed = groupProxies.filterValues { it.health.status == ProxyHealthStatus.FAILED }
        val remaining = groupProxies - failed.keys
        val userProxies = proxies.value[user.id].orEmpty()
        proxies.value = proxies.value + (user.id to (userProxies + (proxyGroupId to remaining)))
        return failed.size
    }

    override suspend fun getProxy(
        user: User,
        id: String,
    ): Proxy? {
        val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        return proxies.value[user.id]
            ?.values
            ?.firstNotNullOfOrNull { it[uuid] }
    }

    override suspend fun getProxyGroup(
        user: User,
        id: String,
    ): ProxyGroup? {
        val name = groups.value[user.id]?.get(id) ?: return null
        return buildGroup(user.id, id, name)
    }

    override suspend fun updateProxyHealth(
        user: User,
        proxyId: UUID,
        health: ProxyHealth,
    ) {
        val userProxies = proxies.value[user.id].orEmpty()
        val updated =
            userProxies.mapValues { (_, groupProxies) ->
                val existing = groupProxies[proxyId]
                if (existing != null) {
                    groupProxies + (proxyId to existing.copy(health = health))
                } else {
                    groupProxies
                }
            }
        proxies.value = proxies.value + (user.id to updated)
    }

    override suspend fun getStaleProxies(
        user: User,
        olderThan: Instant,
    ): List<Proxy> =
        proxies.value[user.id]
            .orEmpty()
            .values
            .flatMap { it.values }
            .filter { proxy ->
                val lastCheckedAt = proxy.health.lastCheckedAt
                lastCheckedAt == null || lastCheckedAt < olderThan
            }
}

package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealth
import com.cereal.client.domain.model.proxy.ProxyHealthStatus
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.repository.ProxyRepository
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemProxyTemplateDataSource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * In-memory proxy repository. Holds no built-in sample data: state is whatever the caller injects
 * via [initialGroups] (empty by default). The `mock` flavor injects sample groups through the DI
 * module; tests pass their own or none. Backed by [MutableStateFlow] so edits and deletes update
 * the table.
 *
 * Concurrent suspend calls land on the IO dispatcher, so all mutating state goes through
 * [mutex] to keep [proxies] and [currentGroups] consistent.
 */
class InMemoryProxyRepository(
    initialGroups: List<ProxyGroup> = emptyList(),
) : ProxyRepository {
    private val mutex = Mutex()
    private val templateDataSource = FileSystemProxyTemplateDataSource()
    private val proxies: MutableMap<String, MutableStateFlow<List<Proxy>>> =
        initialGroups.associateTo(mutableMapOf()) { it.id to MutableStateFlow(it.items.toList()) }

    @Volatile private var currentGroups: List<ProxyGroup> = initialGroups

    // SharedFlow (not StateFlow) because ProxyGroup.equals is ID-only:
    // a renamed group produces a list structurally equal to the previous one,
    // which StateFlow would dedupe and the UI would never see the rename.
    private val groupsFlow: MutableSharedFlow<List<ProxyGroup>> =
        MutableSharedFlow<List<ProxyGroup>>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        ).apply { tryEmit(currentGroups) }

    override suspend fun createProxyGroup(proxyGroup: ProxyGroup) =
        mutex.withLock {
            proxies[proxyGroup.id] = MutableStateFlow(emptyList())
            emitGroupsLocked(currentGroups + proxyGroup.copy(numberOfItems = 0, items = emptySequence()))
        }

    override suspend fun updateProxyGroup(
        id: String,
        name: String,
    ) = mutex.withLock {
        emitGroupsLocked(
            currentGroups.map { group ->
                if (group.id == id) group.copy(name = name) else group
            },
        )
    }

    override suspend fun deleteProxyGroup(proxyGroup: ProxyGroup) =
        mutex.withLock {
            proxies.remove(proxyGroup.id)
            emitGroupsLocked(currentGroups.filter { it.id != proxyGroup.id })
        }

    override suspend fun stampProxyGroupProvider(
        groupId: String,
        provider: ProxyVendor,
        geoLabel: String?,
    ) = mutex.withLock {
        emitGroupsLocked(
            currentGroups.map { group ->
                if (group.id == groupId) group.copy(provider = provider, geoLabel = geoLabel) else group
            },
        )
    }

    override suspend fun getProxyGroups(): Flow<List<ProxyGroup>> = groupsFlow

    override suspend fun getProxiesFlow(proxyGroup: ProxyGroup): Flow<List<Proxy>> = mutex.withLock { proxies.getOrPut(proxyGroup.id) { MutableStateFlow(emptyList()) } }

    override suspend fun createOrUpdateProxy(
        proxy: Proxy,
        group: ProxyGroup,
    ) = mutex.withLock {
        val flow = proxies.getOrPut(group.id) { MutableStateFlow(emptyList()) }
        val current = flow.value
        val updated =
            if (current.any { it.id == proxy.id }) {
                current.map { if (it.id == proxy.id) proxy else it }
            } else {
                current + proxy
            }
        flow.value = updated
        bumpGroupCountLocked(group.id, updated.size)
    }

    override suspend fun deleteProxy(proxy: Proxy) =
        mutex.withLock {
            proxies.forEach { (groupId, flow) ->
                if (flow.value.any { it.id == proxy.id }) {
                    flow.value = flow.value.filter { it.id != proxy.id }
                    bumpGroupCountLocked(groupId, flow.value.size)
                }
            }
        }

    override suspend fun deleteProxiesFromGroup(proxyGroup: ProxyGroup) =
        mutex.withLock {
            proxies[proxyGroup.id]?.value = emptyList()
            bumpGroupCountLocked(proxyGroup.id, 0)
        }

    override suspend fun deleteFailedProxiesFromGroup(proxyGroup: ProxyGroup): Int =
        mutex.withLock {
            val flow = proxies[proxyGroup.id] ?: return@withLock 0
            val before = flow.value
            val after = before.filterNot { it.health.status == ProxyHealthStatus.FAILED }
            if (after.size == before.size) return@withLock 0
            flow.value = after
            bumpGroupCountLocked(proxyGroup.id, after.size)
            before.size - after.size
        }

    override suspend fun getProxiesFromGroup(proxyGroupId: String): List<Proxy> = mutex.withLock { proxies[proxyGroupId]?.value ?: emptyList() }

    override suspend fun getProxiesInGroupCount(proxyGroupId: String): Long = mutex.withLock { proxies[proxyGroupId]?.value?.size?.toLong() ?: 0L }

    override suspend fun readFromFile(file: File): List<Proxy> =
        templateDataSource.readFromTemplate(file).map {
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
    ) = mutex.withLock {
        proxies.forEach { (groupId, flow) ->
            val current = flow.value
            if (current.any { it.id == proxyId }) {
                flow.value = current.map { if (it.id == proxyId) it.copy(health = health) else it }
                bumpGroupCountLocked(groupId, flow.value.size)
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun getStaleProxies(olderThan: Instant): List<Proxy> = emptyList()

    private fun bumpGroupCountLocked(
        groupId: String,
        newCount: Int,
    ) {
        emitGroupsLocked(
            currentGroups.map { group ->
                if (group.id == groupId) group.copy(numberOfItems = newCount) else group
            },
        )
    }

    private fun emitGroupsLocked(newGroups: List<ProxyGroup>) {
        currentGroups = newGroups
        groupsFlow.tryEmit(newGroups)
    }
}

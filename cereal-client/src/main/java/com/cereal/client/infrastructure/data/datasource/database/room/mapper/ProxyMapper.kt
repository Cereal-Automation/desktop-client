package com.cereal.client.infrastructure.data.datasource.database.room.mapper

import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealth
import com.cereal.client.domain.model.proxy.ProxyHealthStatus
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ProxyEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ProxyGroupEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ProxyGroupWithCountEntity
import com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Mapper for converting between domain models and Room entities for proxy operations
 */
class ProxyMapper {
    @OptIn(ExperimentalTime::class)
    fun createProxyEntity(
        proxy: Proxy,
        groupId: String,
    ): ProxyEntity {
        val now =
            Clock.System
                .now()
        return ProxyEntity(
            id = proxy.id,
            groupId = UUID.fromString(groupId),
            host = proxy.address,
            port = proxy.port,
            username = proxy.username,
            password = EncryptedString.from(proxy.password),
            createdAt = now,
            updatedAt = now,
            healthStatus =
                proxy.health.status
                    .takeIf { it != ProxyHealthStatus.UNKNOWN }
                    ?.name,
            lastCheckedAt = proxy.health.lastCheckedAt,
            latencyMs = proxy.health.latencyMs,
            lastError = proxy.health.lastError,
        )
    }

    @OptIn(ExperimentalTime::class)
    fun updateProxyEntity(
        existing: ProxyEntity,
        proxy: Proxy,
    ): ProxyEntity =
        existing.copy(
            host = proxy.address,
            port = proxy.port,
            username = proxy.username,
            password = EncryptedString.from(proxy.password),
            updatedAt =
                Clock.System
                    .now(),
        )

    @OptIn(ExperimentalTime::class)
    fun createProxyGroupEntity(proxyGroup: ProxyGroup): ProxyGroupEntity {
        val now =
            Clock.System
                .now()
        return ProxyGroupEntity(
            id = UUID.fromString(proxyGroup.id),
            name = proxyGroup.name,
            createdAt = now,
            updatedAt = now,
            provider = proxyGroup.provider?.name,
            geoLabel = proxyGroup.geoLabel,
        )
    }

    @OptIn(ExperimentalTime::class)
    fun updateProxyGroupEntity(
        existing: ProxyGroupEntity,
        name: String,
    ): ProxyGroupEntity =
        existing.copy(
            name = name,
            updatedAt =
                Clock.System
                    .now(),
        )

    /**
     * Stamps the provider and geo label on an existing group (used by proxy syncs). Leaves the name and
     * created-at untouched.
     */
    @OptIn(ExperimentalTime::class)
    fun stampProviderEntity(
        existing: ProxyGroupEntity,
        provider: ProxyVendor,
        geoLabel: String?,
    ): ProxyGroupEntity =
        existing.copy(
            provider = provider.name,
            geoLabel = geoLabel,
            updatedAt =
                Clock.System
                    .now(),
        )

    fun toDomain(entity: ProxyEntity): Proxy =
        Proxy(
            id = entity.id,
            address = entity.host,
            port = entity.port,
            username = entity.username,
            password = entity.password?.value,
            health = entity.toHealth(),
        )

    @OptIn(ExperimentalTime::class)
    private fun ProxyEntity.toHealth(): ProxyHealth {
        val status =
            healthStatus?.let { name ->
                runCatching { ProxyHealthStatus.valueOf(name) }.getOrNull()
            } ?: ProxyHealthStatus.UNKNOWN
        return ProxyHealth(
            status = status,
            lastCheckedAt = lastCheckedAt,
            latencyMs = latencyMs,
            lastError = lastError,
        )
    }

    fun toDomain(
        entity: ProxyGroupEntity,
        proxies: List<ProxyEntity>,
    ): ProxyGroup =
        ProxyGroup(
            id = entity.id.toString(),
            name = entity.name,
            numberOfItems = proxies.size,
            items = proxies.asSequence().map { toDomain(it) },
            provider = entity.provider.toProvider(),
            geoLabel = entity.geoLabel,
        )

    fun toDomain(
        entity: ProxyGroupEntity,
        proxiesSequence: Sequence<Proxy>,
    ): ProxyGroup {
        val proxiesList = proxiesSequence.toList()
        return ProxyGroup(
            id = entity.id.toString(),
            name = entity.name,
            numberOfItems = proxiesList.size,
            items = proxiesList.asSequence(),
            provider = entity.provider.toProvider(),
            geoLabel = entity.geoLabel,
        )
    }

    fun toDomain(entity: ProxyGroupWithCountEntity): ProxyGroup =
        ProxyGroup(
            id = entity.id.toString(),
            name = entity.name,
            numberOfItems = entity.proxy_count.toInt(),
            items = emptySequence(),
            // Proxies are loaded separately when needed
            provider = entity.provider.toProvider(),
            geoLabel = entity.geoLabel,
        )

    /** Parses a stored provider enum name, tolerating null and unknown values (returns null). */
    private fun String?.toProvider(): ProxyVendor? = this?.let { runCatching { ProxyVendor.valueOf(it) }.getOrNull() }
}

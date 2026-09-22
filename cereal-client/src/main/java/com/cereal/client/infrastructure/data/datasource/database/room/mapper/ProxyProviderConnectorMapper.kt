package com.cereal.client.infrastructure.data.datasource.database.room.mapper

import com.cereal.client.domain.model.proxy.ProxyProviderConnector
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ProxyProviderConnectorEntity
import kotlin.time.ExperimentalTime

/**
 * Mapper between [ProxyProviderConnector] domain models and [ProxyProviderConnectorEntity] Room entities.
 */
@OptIn(ExperimentalTime::class)
class ProxyProviderConnectorMapper {
    fun toEntity(connector: ProxyProviderConnector): ProxyProviderConnectorEntity =
        ProxyProviderConnectorEntity(
            provider = connector.provider.name,
            connectedAt = connector.connectedAt,
            lastSyncAt = connector.lastSyncAt,
            subUserHash = connector.subUserHash,
            availableTrafficGb = connector.availableTrafficGb,
            subUserCount = connector.subUserCount,
            credentialKey = connector.credentialKey,
        )

    /** Returns `null` when the stored provider name no longer maps to a known [ProxyVendor]. */
    fun toDomain(entity: ProxyProviderConnectorEntity): ProxyProviderConnector? {
        val provider = runCatching { ProxyVendor.valueOf(entity.provider) }.getOrNull() ?: return null
        return ProxyProviderConnector(
            provider = provider,
            connectedAt = entity.connectedAt,
            lastSyncAt = entity.lastSyncAt,
            subUserHash = entity.subUserHash,
            availableTrafficGb = entity.availableTrafficGb,
            subUserCount = entity.subUserCount,
            credentialKey = entity.credentialKey,
        )
    }
}

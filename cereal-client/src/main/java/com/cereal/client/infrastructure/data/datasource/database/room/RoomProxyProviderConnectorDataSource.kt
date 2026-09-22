package com.cereal.client.infrastructure.data.datasource.database.room

import com.cereal.client.domain.model.proxy.ProxyProviderConnector
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.ProxyProviderConnectorDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.ProxyProviderConnectorMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room implementation of [ProxyProviderConnectorDataSource].
 */
class RoomProxyProviderConnectorDataSource(
    private val roomDatabases: RoomDatabases,
) : ProxyProviderConnectorDataSource {
    private val mapper = ProxyProviderConnectorMapper()

    override fun observeConnector(
        user: User,
        provider: ProxyVendor,
    ): Flow<ProxyProviderConnector?> {
        val dao = roomDatabases.getUserDatabase(user).proxyProviderConnectorDao()
        return dao.observeByProvider(provider.name).map { entity -> entity?.let { mapper.toDomain(it) } }
    }

    override suspend fun upsertConnector(
        user: User,
        connector: ProxyProviderConnector,
    ) {
        val dao = roomDatabases.getUserDatabase(user).proxyProviderConnectorDao()
        dao.upsert(mapper.toEntity(connector))
    }

    override suspend fun deleteConnector(
        user: User,
        provider: ProxyVendor,
    ) {
        val dao = roomDatabases.getUserDatabase(user).proxyProviderConnectorDao()
        dao.deleteByProvider(provider.name)
    }
}

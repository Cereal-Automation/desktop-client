package com.cereal.client.infrastructure.data.datasource.database

import com.cereal.client.domain.model.proxy.ProxyProviderConnector
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.model.user.User
import kotlinx.coroutines.flow.Flow

/**
 * Persistence seam for provider-keyed [ProxyProviderConnector] records.
 */
interface ProxyProviderConnectorDataSource {
    fun observeConnector(
        user: User,
        provider: ProxyVendor,
    ): Flow<ProxyProviderConnector?>

    suspend fun upsertConnector(
        user: User,
        connector: ProxyProviderConnector,
    )

    suspend fun deleteConnector(
        user: User,
        provider: ProxyVendor,
    )
}

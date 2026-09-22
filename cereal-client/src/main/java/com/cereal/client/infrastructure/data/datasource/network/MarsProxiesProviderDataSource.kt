package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.domain.model.proxy.ProxyProviderAccount
import com.cereal.client.domain.model.proxy.ProxySubUser
import com.cereal.client.infrastructure.data.datasource.network.marsproxies.MarsProxiesApiClient
import com.cereal.client.infrastructure.data.datasource.network.marsproxies.dto.MarsProxiesAccountResponse
import com.cereal.client.infrastructure.data.datasource.network.marsproxies.dto.MarsProxiesGenerateProxyListRequest
import com.cereal.client.infrastructure.data.datasource.network.marsproxies.dto.MarsProxiesSubUserDto

/**
 * [ProxyProviderDataSource] backed by the MarsProxies API. Thin adapter: delegates to
 * [MarsProxiesApiClient] and maps DTOs to domain types.
 */
class MarsProxiesProviderDataSource(
    private val apiClient: MarsProxiesApiClient,
) : ProxyProviderDataSource {
    override suspend fun validateAndFetchAccount(token: String): ProxyProviderAccount = apiClient.getAccount(token).toDomain()

    override suspend fun fetchSubUsers(token: String): List<ProxySubUser> = apiClient.getSubUsers(token).data.map { it.toDomain() }

    override suspend fun generateProxies(
        token: String,
        request: MarsProxiesGenerateProxyListRequest,
    ): List<String> = apiClient.generateProxyList(token, request)

    private fun MarsProxiesAccountResponse.toDomain(): ProxyProviderAccount =
        ProxyProviderAccount(
            availableTrafficGb = trafficAvailable,
            subUserCount = subUsersCount,
            accountHash = hash,
        )

    private fun MarsProxiesSubUserDto.toDomain(): ProxySubUser =
        ProxySubUser(
            id = id,
            hash = hash,
            username = username,
            password = password,
            trafficAvailable = trafficAvailable,
            trafficUsed = trafficUsed,
        )
}

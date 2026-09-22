package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.domain.model.proxy.ProxyProviderAccount
import com.cereal.client.domain.model.proxy.ProxySubUser
import com.cereal.client.infrastructure.data.datasource.network.marsproxies.dto.MarsProxiesGenerateProxyListRequest

/**
 * Provider-agnostic network seam for proxy providers. Implementations talk to a specific provider's API
 * (e.g. [MarsProxiesProviderDataSource]) and map transport DTOs to domain types.
 */
interface ProxyProviderDataSource {
    /**
     * Validates [token] against the provider account endpoint and returns the account summary.
     *
     * @throws com.cereal.client.infrastructure.data.datasource.network.exception.AuthenticationException if rejected.
     * @throws java.io.IOException on connectivity failure.
     */
    suspend fun validateAndFetchAccount(token: String): ProxyProviderAccount

    /**
     * Lists the sub-users on the account authenticated by [token].
     *
     * @throws java.io.IOException on connectivity failure.
     */
    suspend fun fetchSubUsers(token: String): List<ProxySubUser>

    /**
     * Generates a list of rendered proxy connection strings (`host:port:user:pass`) for [request].
     *
     * @throws java.io.IOException on connectivity failure.
     */
    suspend fun generateProxies(
        token: String,
        request: MarsProxiesGenerateProxyListRequest,
    ): List<String>
}

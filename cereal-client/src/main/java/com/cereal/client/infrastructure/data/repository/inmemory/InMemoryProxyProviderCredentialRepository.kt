package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.repository.ProxyProviderCredentialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [ProxyProviderCredentialRepository] for tests. Tokens are held per provider in a
 * [MutableStateFlow] so getters return live values and setters update them.
 */
class InMemoryProxyProviderCredentialRepository : ProxyProviderCredentialRepository {
    private val tokens = mutableMapOf<ProxyVendor, MutableStateFlow<String>>()

    override suspend fun setToken(
        provider: ProxyVendor,
        token: String,
    ) {
        flowFor(provider).value = token
    }

    override suspend fun getToken(provider: ProxyVendor): Flow<String> = flowFor(provider)

    override fun credentialKey(provider: ProxyVendor): String = "in_memory_token_${provider.name}"

    private fun flowFor(provider: ProxyVendor): MutableStateFlow<String> = tokens.getOrPut(provider) { MutableStateFlow("") }
}

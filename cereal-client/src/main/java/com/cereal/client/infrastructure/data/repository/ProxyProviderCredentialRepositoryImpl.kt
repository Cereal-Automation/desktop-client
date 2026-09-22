package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.repository.ProxyProviderCredentialRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.KeyValueDataSource
import com.cereal.client.infrastructure.data.datasource.preference.ApplicationPreferenceKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Key-value backed [ProxyProviderCredentialRepository]. Tokens are stored under the provider's
 * sensitive preference key (redacted from logs via [ApplicationPreferenceKey.sensitiveKeys]).
 */
class ProxyProviderCredentialRepositoryImpl(
    private val keyValueDataSource: KeyValueDataSource,
    private val userSession: UserSession,
) : ProxyProviderCredentialRepository {
    override suspend fun setToken(
        provider: ProxyVendor,
        token: String,
    ) {
        keyValueDataSource.setStringByKey(credentialKey(provider), token, userSession.requireUser())
    }

    override suspend fun getToken(provider: ProxyVendor): Flow<String> =
        keyValueDataSource.getStringByKey(credentialKey(provider), userSession.requireUser()).map {
            it ?: ""
        }

    override fun credentialKey(provider: ProxyVendor): String =
        when (provider) {
            ProxyVendor.MARSPROXIES -> ApplicationPreferenceKey.MarsProxiesApiToken.key
            else -> error("Unsupported provider: $provider")
        }
}

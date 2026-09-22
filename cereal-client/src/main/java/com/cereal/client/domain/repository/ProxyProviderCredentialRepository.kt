package com.cereal.client.domain.repository

import com.cereal.client.domain.model.proxy.ProxyVendor
import kotlinx.coroutines.flow.Flow

/**
 * Stores and reads the sensitive API token for a proxy provider. A vendor credential is a proxy
 * concern, not a generic application setting, so it lives here rather than on
 * [ApplicationPreferenceRepository]. The persisted [ProxyProviderConnector][com.cereal.client.domain.model.proxy.ProxyProviderConnector]
 * record references the token by [credentialKey] and never holds the token itself.
 */
interface ProxyProviderCredentialRepository {
    /** Stores [provider]'s API token as a sensitive credential (redacted from logs). Pass "" to clear. */
    suspend fun setToken(
        provider: ProxyVendor,
        token: String,
    )

    /** Emits [provider]'s stored token, or "" when none is stored. */
    suspend fun getToken(provider: ProxyVendor): Flow<String>

    /** The reference key under which [provider]'s token is stored (recorded on the connector). */
    fun credentialKey(provider: ProxyVendor): String
}

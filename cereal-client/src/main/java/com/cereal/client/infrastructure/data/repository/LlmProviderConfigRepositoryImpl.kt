package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.client.domain.model.url.WebUrl
import com.cereal.client.domain.repository.LlmProviderConfigRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.KeyValueDataSource
import com.cereal.client.infrastructure.data.datasource.preference.ApplicationPreferenceKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Key-value backed [LlmProviderConfigRepository] on the user's settings store. The whole config is one
 * JSON value under a single sensitive key, so it is written and cleared atomically and stored as one
 * `EncryptedString` (ADR-0001).
 */
class LlmProviderConfigRepositoryImpl(
    private val keyValueDataSource: KeyValueDataSource,
    private val userSession: UserSession,
) : LlmProviderConfigRepository {
    override suspend fun get(): LlmProviderConfig? {
        val stored = keyValueDataSource.getStringByKey(KEY, userSession.requireUser()).first() ?: return null
        // A value this build can't read (corrupt, or a provider a newer build added) reads as "None"
        // rather than breaking Settings or every LLM call.
        return runCatching { Json.decodeFromString<StoredConfig>(stored).toDomain() }.getOrNull()
    }

    override suspend fun save(config: LlmProviderConfig) {
        keyValueDataSource.setStringByKey(KEY, Json.encodeToString(StoredConfig.of(config)), userSession.requireUser())
    }

    override suspend fun clear() {
        keyValueDataSource.deleteValueByKey(KEY, userSession.requireUser())
    }

    @Serializable
    private data class StoredConfig(
        val provider: String,
        val baseUrl: String,
        val apiKey: String? = null,
        val model: String,
    ) {
        fun toDomain() =
            LlmProviderConfig(
                provider = LlmProviderType.valueOf(provider),
                baseUrl = WebUrl.of(baseUrl),
                apiKey = apiKey?.let(::Secret),
                model = model,
            )

        companion object {
            fun of(config: LlmProviderConfig) =
                StoredConfig(
                    provider = config.provider.name,
                    baseUrl = config.baseUrl.value,
                    apiKey = config.apiKey?.reveal(),
                    model = config.model,
                )
        }
    }

    private companion object {
        val KEY = ApplicationPreferenceKey.LlmProviderConfig.key
    }
}

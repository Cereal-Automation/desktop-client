package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.repository.LlmProviderConfigRepository

/** In-memory [LlmProviderConfigRepository] for the `mock` flavor and tests. Starts unconfigured unless seeded. */
class InMemoryLlmProviderConfigRepository(
    initial: LlmProviderConfig? = null,
) : LlmProviderConfigRepository {
    @Volatile
    private var config: LlmProviderConfig? = initial

    override suspend fun get(): LlmProviderConfig? = config

    override suspend fun save(config: LlmProviderConfig) {
        this.config = config
    }

    override suspend fun clear() {
        config = null
    }
}

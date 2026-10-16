package com.cereal.client.domain.repository

import com.cereal.client.domain.model.llm.LlmProviderConfig

/**
 * Owns the current user's [LlmProviderConfig] (Settings → AI). A vendor credential, so it has its own
 * repository rather than living on [ApplicationPreferenceRepository]. No saved config means the user
 * chose provider "None".
 */
interface LlmProviderConfigRepository {
    /** The saved config, or null when none is set up. Read fresh on every call. */
    suspend fun get(): LlmProviderConfig?

    /** Replaces the saved config, key included. */
    suspend fun save(config: LlmProviderConfig)

    /** Deletes the whole config, key included. */
    suspend fun clear()
}

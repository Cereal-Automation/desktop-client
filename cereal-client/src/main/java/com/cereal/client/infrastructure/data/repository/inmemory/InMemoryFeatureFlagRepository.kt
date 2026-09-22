package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.featureflag.FeatureFlag
import com.cereal.client.domain.repository.FeatureFlagRepository

/**
 * In-memory [FeatureFlagRepository] for tests. Lets a test force specific flag states regardless of
 * build type. Unset flags fall back to their [FeatureFlag.debug] default (tests run in a dev-like
 * context).
 */
class InMemoryFeatureFlagRepository(
    overrides: Map<FeatureFlag, Boolean> = emptyMap(),
) : FeatureFlagRepository {
    private val overrides = overrides.toMutableMap()

    fun set(
        flag: FeatureFlag,
        enabled: Boolean,
    ) {
        overrides[flag] = enabled
    }

    override fun isEnabled(flag: FeatureFlag): Boolean = overrides[flag] ?: flag.debug
}

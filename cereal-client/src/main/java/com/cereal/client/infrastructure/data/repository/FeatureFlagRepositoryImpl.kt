package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.featureflag.FeatureFlag
import com.cereal.client.domain.repository.FeatureFlagRepository

/**
 * Resolves each [FeatureFlag] to its build-specific default: [FeatureFlag.debug] for development
 * builds, [FeatureFlag.release] for shipped builds.
 *
 * The build type is injected as [isDebugBuild] (wired from `BuildConfig.IS_DEBUG` in the DI module)
 * rather than read here, so the mapping logic is unit-testable for both build types.
 */
class FeatureFlagRepositoryImpl(
    private val isDebugBuild: Boolean,
) : FeatureFlagRepository {
    override fun isEnabled(flag: FeatureFlag): Boolean = if (isDebugBuild) flag.debug else flag.release
}

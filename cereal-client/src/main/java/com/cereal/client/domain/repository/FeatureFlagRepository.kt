package com.cereal.client.domain.repository

import com.cereal.client.domain.model.featureflag.FeatureFlag

/**
 * Reads the state of compile-time [FeatureFlag]s.
 *
 * Unlike the other repositories this one is synchronous (no `suspend`/`Flow`): feature flags are
 * effectively constants for the lifetime of a process, so reactivity would be ceremony with no
 * payoff. Inject it directly wherever a flag is consumed — including ViewModels — as a documented
 * exception to the "presentation only depends on interactors" rule, since a flag check is
 * cross-cutting build configuration rather than a use case.
 */
interface FeatureFlagRepository {
    /**
     * Returns whether [flag] is enabled for the current build.
     */
    fun isEnabled(flag: FeatureFlag): Boolean
}

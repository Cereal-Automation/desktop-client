package com.cereal.client.application.interactor.brand

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.Interactor
import com.cereal.client.domain.provider.AuthProvider

/**
 * Decides whether the current user is entitled to a white-label build's Brand scripts (see
 * docs/adr/0004). A Brand is entitled only when the logged-in user holds a marketplace subscription
 * for every one of its [ApplicationConfig.brandScriptIds]. Stock Cereal (no Brand) is always
 * entitled — the gate is transparent there.
 */
class GetBrandEntitlementInteractor(
    private val applicationConfig: ApplicationConfig,
    private val authProvider: AuthProvider,
) : Interactor<BrandEntitlement, Interactor.None>() {
    override suspend fun run(params: Interactor.None): BrandEntitlement {
        val requiredScriptIds = applicationConfig.brandScriptIds
        if (!applicationConfig.isBranded || requiredScriptIds.isEmpty()) {
            return BrandEntitlement.Entitled
        }

        // Ignore the cache: this gate runs right after login, and an out-of-date snapshot would
        // wrongly paywall a user who just purchased (or wrongly admit a lapsed one).
        val subscribedScriptIds =
            authProvider
                .getSubscriptions(ignoreCache = true)
                .map { it.entitlement.publicIdentifier }
                .toSet()

        val missing = requiredScriptIds.filterNot { it in subscribedScriptIds }
        return if (missing.isEmpty()) {
            BrandEntitlement.Entitled
        } else {
            BrandEntitlement.MissingSubscription(missing)
        }
    }
}

/** Outcome of [GetBrandEntitlementInteractor]. */
sealed class BrandEntitlement {
    /** Every required Brand script is subscribed (or this is stock Cereal). */
    data object Entitled : BrandEntitlement()

    /** One or more required Brand scripts have no subscription — the user must purchase. */
    data class MissingSubscription(
        val missingScriptIds: List<String>,
    ) : BrandEntitlement()
}

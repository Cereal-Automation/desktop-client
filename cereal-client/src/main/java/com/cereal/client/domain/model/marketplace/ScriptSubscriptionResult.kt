package com.cereal.client.domain.model.marketplace

/**
 * The outcome of subscribing the current user to a marketplace script.
 *
 * Modelled as a sealed hierarchy so that a checkout URL only exists in the one state where it is
 * meaningful ([CheckoutRequired]) — making the "checkout initiated but no URL" state unrepresentable
 * above the repository boundary.
 */
sealed class ScriptSubscriptionResult {
    /** The user was subscribed as part of this call (free scripts, or an already-paid entitlement). */
    data object Subscribed : ScriptSubscriptionResult()

    /** The user already had an active subscription; nothing changed. */
    data object AlreadySubscribed : ScriptSubscriptionResult()

    /**
     * Payment is required before the subscription becomes active. The user must complete checkout at
     * [checkoutUrl] before the script can be installed.
     */
    data class CheckoutRequired(
        val checkoutUrl: String,
    ) : ScriptSubscriptionResult()
}

package com.cereal.client.domain.model.script

/**
 * The stable, install-time identity of a script (its package identifier, e.g. `com.cereal.shopify`).
 *
 * The same identity is referenced under three historical names across the codebase —
 * [Manifest.packageName], [ScriptEntitlement.publicIdentifier] and the marketplace
 * `publicIdentifier` — which were previously three unrelated raw `String`s (a primitive-obsession
 * finding). This alias unifies that vocabulary into one meaningful, self-documenting type.
 *
 * It is a `typealias` rather than a validated `@JvmInline value class` on purpose: the identity is
 * compared for equality against [Manifest.packageName] and used as a map key / set member and a
 * repository, provider, `Params` and presentation-callback argument in many places. A wrapper type
 * would force `.value` unwrapping at every one of those seams (or ripple the wrapper across the
 * infrastructure, presentation and navigation layers) for no behavioural gain — non-blank-ness is
 * already enforced where the identity originates (see [Manifest] and [ScriptEntitlement]).
 */
typealias ScriptPublicIdentifier = String

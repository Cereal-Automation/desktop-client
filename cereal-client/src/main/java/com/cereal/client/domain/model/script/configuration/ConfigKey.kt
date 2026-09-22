package com.cereal.client.domain.model.script.configuration

/**
 * The identity of a single configuration item within a script's configuration — the key under which
 * its value is stored and looked up (see [ScriptConfigurationItemDefinition.key] and
 * `ScriptConfigurationValues`, which is keyed by this).
 *
 * A `typealias` (not a validated value class): the key is purely a map key, and the configuration
 * value hierarchy it indexes is owned elsewhere, so wrapping it would ripple `.value` through that
 * layer for no behavioural gain. This still lifts the field out of anonymous `String` primitive
 * obsession, per the domain guide's "wrap meaningful identifiers in type aliases at minimum".
 */
typealias ConfigKey = String

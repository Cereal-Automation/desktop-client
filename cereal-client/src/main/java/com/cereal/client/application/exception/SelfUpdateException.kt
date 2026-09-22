package com.cereal.client.application.exception

/**
 * Telemetry signal for a self-update that failed *after* clearing its preconditions — the mechanism
 * itself broke on a machine where it should have worked (expansion, signature/Gatekeeper assertion,
 * atomic swap, or a missing bundled helper).
 *
 * These steps degrade to a manual-install fallback by returning false rather than throwing, so they
 * never reach the [com.cereal.client.application.Interactor] pipeline. This exception exists purely
 * to carry a descriptive, greppable value to [CrashReporter.report] so those failures are visible in
 * Sentry — unlike expected no-elevation degradation (e.g. a standard user who cannot write the
 * install location), which stays log-only to avoid drowning the signal in noise. It is never thrown
 * across a layer boundary or surfaced to the user.
 */
class SelfUpdateException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

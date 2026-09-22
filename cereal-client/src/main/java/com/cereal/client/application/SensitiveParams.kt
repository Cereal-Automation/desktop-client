package com.cereal.client.application

/**
 * Marker for [Interactor]/[FlowInteractor] `Params` that carry secrets — passwords, API tokens,
 * webhook URLs, and the like.
 *
 * When an interactor fails, the base class attaches the failing `Params` to the Sentry report as
 * debugging context via `toString()`. `Params` implementing this interface are redacted instead,
 * so credentials never leave the user's machine. Mark any `Params` holding a secret with it; the
 * exception type and stack trace still identify which interactor failed.
 */
interface SensitiveParams

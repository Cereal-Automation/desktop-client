package com.cereal.client.domain.model.featureflag

/**
 * A compile-time on/off switch that gates an in-progress or risky feature behind a branch in the
 * code. The default is chosen by build type: [debug] applies to local development builds
 * (`./gradlew run`, `IS_DEBUG=true`) and [release] applies to shipped builds (`runRelease` and
 * distributed artifacts, `IS_DEBUG=false`).
 *
 * Flags are read synchronously through [com.cereal.client.domain.repository.FeatureFlagRepository] —
 * they never change while the app runs, so flipping one means editing this enum and rebuilding.
 *
 * **Temporary by intent**: a flag exists to hide a feature until it ships, then is deleted along with
 * its now-dead `if (isEnabled(...))` branch. Keep this enum small.
 *
 * See `docs/adr/0006-feature-flags-are-compile-time-debug-keyed.md`.
 */
enum class FeatureFlag(
    val debug: Boolean,
    val release: Boolean,
) {
    /**
     * The MarsProxies proxy-provider connector (connect/disconnect, sync into a group, group
     * attribution + re-sync, and the post-sync background health check). On in dev so it can be
     * exercised; off in shipped builds until the connector is ready to ship. Delete this flag and its
     * `if (isEnabled(...))` branches once it ships.
     */
    PROXY_PROVIDER_CONNECTOR(debug = true, release = false),

    /**
     * "Sign in with Google" on the login screen (backend-brokered desktop OAuth). Enabled in both dev
     * and shipped builds now that the brokered backend endpoints are deployed. Delete this flag and
     * its `if (isEnabled(...))` branch once it has proven out in release.
     */
    GOOGLE_SIGN_IN(debug = true, release = true),

    /**
     * "Sign in with Discord" on the login screen (backend-brokered desktop OAuth, same broker as
     * [GOOGLE_SIGN_IN]). Enabled in both dev and shipped builds now that the Discord OAuth app is
     * registered and its backend credentials are set. Delete this flag and its `if (isEnabled(...))`
     * branch once it has proven out in release.
     */
    DISCORD_SIGN_IN(debug = true, release = true),
}

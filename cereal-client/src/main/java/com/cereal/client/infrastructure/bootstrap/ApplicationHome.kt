package com.cereal.client.infrastructure.bootstrap

import com.cereal.client.application.Environment
import com.cereal_automation.cereal_client.BuildConfig
import java.io.File

/**
 * Resolves the application's data directory from [BuildConfig] alone, without dependency injection.
 *
 * [com.cereal.client.infrastructure.CerealConfiguration] is the normal way to reach this path, but it
 * is only available once Koin has started. Bootstrap code that runs earlier — see
 * [BootstrapPreferences] — needs the same directory, and a second copy of this computation is how a
 * user's data ends up split across two directories after a brand or environment change. So the
 * computation lives here and `CerealConfiguration` delegates to it.
 *
 * The directory name composes two orthogonal build axes, exactly as `CerealConfiguration` documents:
 * the white-label Brand's own directory (or `Cereal` for stock builds, see `docs/adr/0003`) plus an
 * environment suffix, so a branded acceptance build never shares a directory with stock production.
 */
object ApplicationHome {
    /**
     * Environment differentiator appended to the directory name. Production is unsuffixed because it
     * is the name users see; the other environments are developer-facing.
     */
    val environmentSuffix: String =
        when (BuildConfig.ENVIRONMENT) {
            Environment.ACCEPTANCE -> "-Acc"
            Environment.LOCAL -> "-Local"
            Environment.PRODUCTION -> ""
        }

    private val brandDirectoryName: String =
        BuildConfig.BRAND_ID.ifBlank { null }?.let { BuildConfig.BRAND_HOME_DIR } ?: "Cereal"

    /**
     * `~/<Brand><-Env>`, e.g. `~/Cereal`, `~/Cereal-Acc`.
     *
     * Recomputed on every access rather than cached, because `user.home` is not immutable in
     * practice: [com.cereal.client.smoke.SmokeTest] redirects it to a throwaway directory before
     * booting the app. Caching would make the resolved path depend on whether something happened to
     * touch this object before that override — a class-initialisation order trap. Constructing a
     * [File] is cheap enough that there is nothing to gain from holding one.
     */
    val directory: File
        get() = File(System.getProperty("user.home"), brandDirectoryName + environmentSuffix)
}

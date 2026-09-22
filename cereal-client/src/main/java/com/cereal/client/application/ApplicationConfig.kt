package com.cereal.client.application

import com.cereal.client.domain.model.OperatingSystemType
import java.io.File

interface ApplicationConfig {
    val name: String
    val title: String
    val appIcon: String
    val versionName: String

    /**
     * The operating system this build runs on. Resolved from build-time configuration in the
     * infrastructure layer, so call sites depend on this rather than reading `BuildConfig`.
     */
    val operatingSystem: OperatingSystemType

    /**
     * White-label Brand identity (see docs/adr/0003). `null` for the stock Cereal client; a
     * non-null id means this is a locked, rebranded appliance build.
     */
    val brandId: String?

    /**
     * True when this is a white-label build (a non-blank [brandId]). Prefer this over inspecting
     * [brandId] directly at call sites — it is the single definition of "is the platform locked."
     */
    val isBranded: Boolean
        get() = !brandId.isNullOrBlank()

    /**
     * The marketplace script identities (`publicIdentifier`) this Brand is locked to. Empty for
     * stock Cereal. The appliance auto-installs and exposes only these.
     */
    val brandScriptIds: List<String>

    /**
     * Deep-link opened by the branded paywall when a required subscription is missing.
     * `null` for stock Cereal (which has no paywall).
     */
    val paywallUrl: String?

    /**
     * Auto-update feed path segment under the downloads host
     * (`<host>/<updateFeedPath>/latest-<os>.json`). `null` for stock Cereal (uses the default feed).
     */
    val updateFeedPath: String?

    /**
     * Brand primary color as packed ARGB ([androidx.compose.ui.graphics.Color] is a presentation
     * type, so the value is carried as a `Long` here). `null` = use the default Cereal palette.
     */
    val brandPrimaryColorArgb: Long?

    /*
     * Social URL's
     * */
    val websiteUrl: String
    val githubUrl: String
    val discordUrl: String
    val privacyPolicyUrl: String
    val statusUrl: String

    /*
     * Application home directory
     * */
    val homeDirectory: File

    /*
     * Logs path
     * */
    val logsDirectory: File

    /*
     * Database
     * */
    val databaseDirectory: File
    val databaseName: String

    /*
     * Script paths
     * */
    val getScriptsDirectory: File

    /*
     * Configuration path
     * */
    val scriptConfigurationDirectory: File

    /**
     * Cereal marketplace urls
     */
    val marketplaceBaseUrl: String
    val marketplaceViewProfileUrl: String
    val marketplaceViewSubscriptionsUrl: String
    val marketplaceRegisterUrl: String
    val marketplaceForgotPasswordUrl: String
    val marketplaceSearchScriptsUrl: String
    val marketplacePublicKey: String

    /**
     * SPKI pins (`sha256/<base64>`) for the marketplace API host. Pin the intermediate CA and/or
     * root (the API is Cloudflare/Google-fronted, so the leaf rotates) and ship a backup pin so
     * the set can be rotated by release. An empty list disables pinning.
     */
    val marketplaceApiSSLPins: List<String>

    /**
     * SPKI pins (`sha256/<base64>`) for the downloads/update host (`latest-<os>.json` metadata and
     * the installer binary). Same pinning strategy as [marketplaceApiSSLPins]; empty disables it.
     */
    val downloadsSSLPins: List<String>

    /**
     * Base URL for the MarsProxies residential API (proxy-provider connector). Endpoints are resolved
     * relative to this (`<base>/v1/residential/me`, `<base>/v1/residential/subusers`).
     */
    val marsProxiesBaseUrl: String

    /**
     * SPKI pins (`sha256/<base64>`) for the MarsProxies API host. Same pinning strategy as
     * [marketplaceApiSSLPins]; empty disables pinning.
     */
    val marsProxiesApiSSLPins: List<String>

    /**
     * RSA public key used to verify the signature on auto-update release metadata
     * (`latest-<os>.json`). Its private counterpart lives only in the release pipeline
     * (the `RELEASE_PRIVATE_KEY` CI secret) and is kept separate from the marketplace key.
     */
    val releasePublicKey: String

    /**
     * Encryption keys
     */
    val databaseEncryptionKey: String
    val fileEncryptionKey: String

    /**
     * Whether this is a store build (Microsoft Store or Apple App Store).
     * Store builds redirect users to the respective store for updates instead of downloading directly.
     */
    val isStoreBuild: Boolean

    /**
     * Whether guest login is enabled.
     */
    val isGuestLoginEnabled: Boolean
}

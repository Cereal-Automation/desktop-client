package com.cereal.client.infrastructure

import com.cereal.client.Sekret
import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.OperatingSystemType
import com.cereal.client.infrastructure.bootstrap.ApplicationHome
import com.cereal_automation.cereal_client.BuildConfig
import java.io.File

open class CerealConfiguration(
    final override val marketplaceBaseUrl: String = "https://marketplace.cereal-automation.com/",
    final override val marketplacePublicKey: String = """-----BEGIN PUBLIC KEY-----
      MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA/SASjlyr9cbL40MxfAnY
      ivPNc9bsEdnrzfMUxe9rpthzKO5JvoDv4TfnDQ2sgCwj7R+kf9Ei9DQG7E3da1N+
      ZtRwx8vyuSEoeTtZCxuzDbHZLncsU8/bGOvSkzeYJmOuY1wRrkXa+pC31LgBhF1P
      42qZ7oInuNa9e+rfum8RhHRopY4pg5ybCsuiEODgYnHOKdGw4byrqBmWH4gjtfci
      LBG/yjmR18beyX1Q1cbSh6muKaYW5esgx265Xdi3aUx3lwAynoo+E36wColKUv3x
      ttg0MRrjIg9mjReitwDMUokN/jllIRNqshCm0obJbtUsKdYPeqRWHEQMD29ICYM1
      SZBShbFrGNYy+z4kzLWEknLz3nC6HCph1usawfpgMeuCdibHl+4CrsTDhquvlUnF
      213OYKtan2+ti0JM5CdW8mlsZh81r6YurxOFxl9fa5Sgeby4x5Z5JI0+ni0/uJwf
      Y/w8QqnKI3raJNJC4TAUF1av0wmIgiO2T+hoglGqWHyAO4L5oU02aJTDftZJMgE0
      M9Z+6pEJhE8mZ0p+7Qw7Svxr3MyTWs+AsGDarW/AE5BM2fADQTDIkuBIlO/3CmZf
      KPUr2TsjwENnPi4InPtDiVlEdrFjucPFvb2rhP8bm5ApT2bEThdivFmau35r2jqW
      JswJpb9YnCacxsM0UKeSBKkCAwEAAQ==
      -----END PUBLIC KEY-----""",
    // SPKI pins for the marketplace API host. Pinned to the Google Trust Services intermediate
    // (CN=WE1) with the GTS Root R4 as a backup — both are far more stable than the Cloudflare/GTS
    // leaf, which rotates on a ~90-day cycle. OkHttp accepts the chain if ANY pin matches ANY cert
    // in it, so this survives leaf rotation while still rejecting an unexpected CA. To rotate:
    // fetch the chain (`openssl s_client -connect <host>:443 -showcerts`), recompute the SPKI hash
    // for each cert (`openssl x509 -pubkey | openssl pkey -pubin -outform der | openssl dgst
    // -sha256 -binary | openssl enc -base64`), and ship the new pin here ahead of the CA change.
    final override val marketplaceApiSSLPins: List<String> =
        listOf(
            "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=", // GTS WE1 intermediate
            "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=", // GTS Root R4 (backup)
        ),
    // The downloads/update host (downloads.cereal-automation.com) sits behind the same GTS chain,
    // so it shares the intermediate + root pins. Kept as a separate property so the two hosts can
    // be rotated independently if their chains ever diverge.
    final override val downloadsSSLPins: List<String> =
        listOf(
            "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=", // GTS WE1 intermediate
            "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=", // GTS Root R4 (backup)
        ),
    final override val marsProxiesBaseUrl: String = "https://api.marsproxies.com/",
    // No pins yet for the MarsProxies host — its cert chain hasn't been characterised. Add SPKI pins
    // here (intermediate + backup root) once the chain is known; empty disables pinning for now.
    final override val marsProxiesApiSSLPins: List<String> = emptyList(),
    // Dedicated release-signing key. Its private counterpart is the RELEASE_PRIVATE_KEY CI secret
    // used by .github/actions/create-latest-version-json to sign latest-<os>.json (#484). Kept
    // separate from marketplacePublicKey so the marketplace and release-signing keys never share.
    final override val releasePublicKey: String = """-----BEGIN PUBLIC KEY-----
      MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA1Uf+w7zu1zN5EDPxdRlx
      svM0uFSZZZm7rIA65zlC9cGXanPYVy8Sih1FHAqOxJGXNCY02qzDZBEc4OwtjIMF
      d7oieDZwPkfdzR9RUyM1Y75G5Jxlgr/FIVcLdUy8TsbJktS23FfcrTbybWRkeU1L
      FWUw1L3QN1ywxMnruQhsZPw8IxoybO3rbOjvn+lWEm10oqTDxWEfO0WiPKQ/4Y6h
      gGwHPgdxqAWbIVJLImhY7wltmQro552lKVRGJAjZem+SiNyWrmBMfT4Aa4MmnDVP
      IDZjV05VPmBfsogzRHZAf1n13Cywlg5XjXbCRnp3G4WyskmDzjltgdAgXrMKJ1J8
      yOjO3H6CNWia8Yu9VmY+GD5hJJdQQ9xlJOCNM8bFP+XdYlSeVVIF1xkCP6qGDn5A
      tcIR6bkYI41QMxX9Ld0RfEdtamZ59BK74APwn7qo4PPq7d9HGQnPc6PD+Yns/5sy
      8CX/vqXG3OXdQ5Ta61RBYxvFC2ZvyXD522dsDbTwYnXnKM3FcToBDjTWn5v98kNj
      DNJpCPcS2eg1A1V2gFXK6B2vlR2sDl4K9ibAyKy2j5Vrh1ZbK2G+nKtNXaXpSEne
      2cGYAad/hHb/nWF1iZEV+wjK3XxjUc9QwwPXZk6CkwpFO2FDzoP9ykR+ioe181L3
      nbjarVYw5tFJTYsqc1iT/FMCAwEAAQ==
      -----END PUBLIC KEY-----""",
) : ApplicationConfig {
    // White-label Brand identity, baked in at build time via `-Pbrand` (see docs/adr/0003).
    // Empty BRAND_ID = stock Cereal. The environment differentiator (-Acc/-Local) comes from
    // [ApplicationHome] and composes with the Brand: a branded build still gets its own
    // per-environment data directory so dev/acceptance/prod never collide.
    override val brandId: String? = BuildConfig.BRAND_ID.ifBlank { null }
    override val brandScriptIds: List<String> =
        BuildConfig.BRAND_SCRIPT_IDS
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    override val paywallUrl: String? = BuildConfig.BRAND_PAYWALL_URL.ifBlank { null }
    override val updateFeedPath: String? = BuildConfig.BRAND_UPDATE_FEED_PATH.ifBlank { null }
    override val brandPrimaryColorArgb: Long? = parseColorArgb(BuildConfig.BRAND_COLOR_PRIMARY)

    // Display name is the Brand's full product name (no environment suffix — the user never sees
    // "-Local"); the stock client keeps its environment-suffixed name.
    final override val name =
        if (brandId != null) BuildConfig.BRAND_NAME else "Cereal${ApplicationHome.environmentSuffix}"
    override val title = name
    override val appIcon = "application.svg"
    override val versionName = BuildConfig.APP_VERSION

    override val websiteUrl = "https://cereal-automation.com/"
    override val githubUrl = "https://github.com/Cereal-Automation"
    override val discordUrl = "https://cereal-automation.com/contact"
    override val privacyPolicyUrl = "https://cereal-automation.com/privacy-policy/"
    override val statusUrl = "https://status.cereal-automation.com/"
    private val normalizedMarketplaceBaseUrl = "${marketplaceBaseUrl.trimEnd('/')}/"
    override val marketplaceViewProfileUrl = "${normalizedMarketplaceBaseUrl}account"
    override val marketplaceSearchScriptsUrl = "${normalizedMarketplaceBaseUrl}scripts"
    override val marketplaceViewSubscriptionsUrl = "${normalizedMarketplaceBaseUrl}account#billing"
    override val marketplaceRegisterUrl = "${normalizedMarketplaceBaseUrl}register?from_app=true"
    override val marketplaceForgotPasswordUrl = "${normalizedMarketplaceBaseUrl}password-reset/request"

    // Shared with the pre-DI bootstrap so both agree on one data directory (see [ApplicationHome]).
    final override val homeDirectory = ApplicationHome.directory
    override val logsDirectory = File(homeDirectory, "Logs")
    override val databaseDirectory = File(homeDirectory, "Data")
    override val databaseName = "app.db"
    final override val getScriptsDirectory = File(homeDirectory, "Scripts")
    override val scriptConfigurationDirectory = File(File(homeDirectory, "Data"), "Configuration")
    override val databaseEncryptionKey =
        Sekret.databaseEncryptionKey(BuildConfig.SEKRET_KEY)
            ?: throw RuntimeException("Unable to load key.")
    override val fileEncryptionKey =
        Sekret.fileEncryptionKey(BuildConfig.SEKRET_KEY)
            ?: throw RuntimeException("Unable to load key.")
    override val isStoreBuild = BuildConfig.IS_STORE_BUILD

    @Suppress("KotlinConstantConditions")
    override val operatingSystem: OperatingSystemType =
        when (BuildConfig.OPERATING_SYSTEM) {
            "windows" -> OperatingSystemType.Windows
            "macos" -> OperatingSystemType.MacOS
            "linux" -> OperatingSystemType.Linux
            else -> throw UnsupportedOperationException("Unknown OS: ${BuildConfig.OPERATING_SYSTEM}")
        }

    // Guest login is disabled for white-label builds: a Brand script is gated by a marketplace
    // subscription, which a guest account cannot hold (see docs/adr/0004).
    override val isGuestLoginEnabled = !isBranded

    private companion object {
        private const val RGB_HEX_LENGTH = 6
        private const val ARGB_HEX_LENGTH = 8
        private const val HEX_RADIX = 16
        private const val OPAQUE_ALPHA_MASK = 0xFF000000L

        /**
         * Parse a `#RRGGBB` (or `#AARRGGBB`) hex string into packed ARGB. Returns `null` for a
         * blank/malformed value so the theme falls back to the default Cereal palette. A 6-digit
         * value is given full opacity.
         */
        fun parseColorArgb(hex: String): Long? {
            val cleaned = hex.trim().removePrefix("#")
            if (cleaned.length != RGB_HEX_LENGTH && cleaned.length != ARGB_HEX_LENGTH) return null
            val value = cleaned.toLongOrNull(radix = HEX_RADIX) ?: return null
            return if (cleaned.length == RGB_HEX_LENGTH) value or OPAQUE_ALPHA_MASK else value
        }
    }
}

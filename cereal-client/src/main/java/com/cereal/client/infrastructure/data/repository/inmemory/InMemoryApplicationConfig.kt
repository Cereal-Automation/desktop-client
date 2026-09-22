package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.OperatingSystemType
import java.io.File

/**
 * Sekret-free [ApplicationConfig] for tests. The production `CerealConfiguration` resolves
 * encryption keys through the Sekret native library in its constructor, which isn't loaded in the
 * test JVM — so screen tests substitute this static instance instead.
 */
class InMemoryApplicationConfig(
    private val baseDir: File = File(System.getProperty("java.io.tmpdir"), "cereal-screen-tests"),
    override val name: String = "Cereal",
    // Detect the real host OS at runtime: the production CerealConfiguration resolves this from
    // build-time config, but this fake backs the sandboxed `mock` flavor too, where picking the
    // right OS-specific datasource (e.g. notifications) must follow the actual host, not a constant.
    override val operatingSystem: OperatingSystemType = hostOperatingSystem(),
) : ApplicationConfig {
    override val title: String = "Cereal"
    override val appIcon: String = ""
    override val versionName: String = "0.0.0-test"

    override val brandId: String? = null
    override val brandScriptIds: List<String> = emptyList()
    override val paywallUrl: String? = null
    override val updateFeedPath: String? = null
    override val brandPrimaryColorArgb: Long? = null

    override val websiteUrl: String = "https://cereal-automation.com/"
    override val githubUrl: String = "https://github.com/Cereal-Automation"
    override val discordUrl: String = "https://discord.gg/cereal"
    override val privacyPolicyUrl: String = "https://cereal-automation.com/privacy"
    override val statusUrl: String = "https://status.cereal-automation.com/"

    override val homeDirectory: File = baseDir
    override val logsDirectory: File = File(baseDir, "logs")
    override val databaseDirectory: File = File(baseDir, "db")
    override val databaseName: String = "cereal-test.db"
    override val getScriptsDirectory: File = File(baseDir, "scripts")
    override val scriptConfigurationDirectory: File = File(baseDir, "config")

    override val marketplaceBaseUrl: String = "https://marketplace.cereal-automation.com/"
    override val marketplaceViewProfileUrl: String = "${marketplaceBaseUrl}account"
    override val marketplaceViewSubscriptionsUrl: String = "${marketplaceBaseUrl}account#billing"
    override val marketplaceRegisterUrl: String = "${marketplaceBaseUrl}register"
    override val marketplaceForgotPasswordUrl: String = "${marketplaceBaseUrl}forgot-password"
    override val marketplaceSearchScriptsUrl: String = "${marketplaceBaseUrl}scripts"
    override val marketplacePublicKey: String = ""
    override val marketplaceApiSSLPins: List<String> = emptyList()
    override val downloadsSSLPins: List<String> = emptyList()
    override val marsProxiesBaseUrl: String = "https://api.marsproxies.com/"
    override val marsProxiesApiSSLPins: List<String> = emptyList()
    override val releasePublicKey: String = ""

    override val databaseEncryptionKey: String = "test-database-key"
    override val fileEncryptionKey: String = "test-file-key"

    override val isStoreBuild: Boolean = false
    override val isGuestLoginEnabled: Boolean = true

    private companion object {
        fun hostOperatingSystem(): OperatingSystemType {
            val osName = System.getProperty("os.name").orEmpty().lowercase()
            return when {
                osName.contains("win") -> OperatingSystemType.Windows
                osName.contains("mac") || osName.contains("darwin") -> OperatingSystemType.MacOS
                else -> OperatingSystemType.Linux
            }
        }
    }
}

package com.cereal.client

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.Environment
import com.cereal.client.domain.model.featureflag.FeatureFlag
import com.cereal.client.domain.repository.FeatureFlagRepository
import com.cereal.client.infrastructure.crashreporting.CrashReportingClient
import com.cereal.client.infrastructure.di.Injector
import com.cereal_automation.cereal_client.BuildConfig
import dev.datlag.sekret.NativeLoader
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.java.KoinJavaComponent.get
import org.slf4j.LoggerFactory
import java.io.File
import java.security.MessageDigest
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object App {
    private const val FILE_HASH_BUFFER_SIZE = 8192

    private val logger = LoggerFactory.getLogger(App::class.java)

    fun initialize(): Koin {
        // The sandboxed `mock` flavor runs against in-memory repositories and a Sekret-free
        // ApplicationConfig, so the native secrets library is neither available nor needed.
        if (BuildConfig.FLAVOR != "mock") {
            val isSekretLoaded =
                NativeLoader.loadLibrary(
                    "sekret",
                    System.getProperty("compose.application.resources.dir")?.let { File(it) },
                )

            if (!isSekretLoaded) {
                throw RuntimeException("Unable to load secrets.")
            }
        }

        // Crash reporting deliberately starts before Koin, so that startup crashes are reported at
        // all — which is why the opt-out it consults lives in the pre-DI bootstrap store.
        CrashReportingClient.startIfEnabled()

        val koin = Injector.initialize()
        val applicationConfig = get<ApplicationConfig>(ApplicationConfig::class.java)

        assertCertificatePinningEnabled(applicationConfig)

        // Attached here rather than in CrashReportingClient.start() because the home directory is
        // only known once Koin is up. A no-op when the user has opted out and no client is running.
        Sentry.configureScope { scope ->
            scope.setContexts("home_dir", applicationConfig.homeDirectory.path)
        }

        // Hashing every script file blocks startup for users with large script libraries.
        // Defer to a background coroutine — the context is only needed for later crashes.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val scriptDirFiles =
                applicationConfig.getScriptsDirectory
                    .walkTopDown()
                    .filter { it.isFile }
                    .map {
                        val path = it.relativeTo(applicationConfig.getScriptsDirectory).path
                        val size = it.length()
                        val hash = getFileHash(it)
                        "$path ($size bytes, $hash)"
                    }.toList()
            Sentry.configureScope { scope ->
                scope.setContexts("script_dir_files", scriptDirFiles)
            }
        }

        logActiveFeatureFlags(koin)

        return koin
    }

    /**
     * Logs the resolved state of every [FeatureFlag] at startup so the active set is visible in the
     * logs for the current build (the flags themselves are compile-time and never change at runtime).
     */
    private fun logActiveFeatureFlags(koin: Koin) {
        val featureFlagRepository = koin.get<FeatureFlagRepository>()
        val states =
            FeatureFlag.entries
                .joinToString { "${it.name}=${featureFlagRepository.isEnabled(it)}" }
                .ifEmpty { "none" }
        logger.info("Feature flags: $states")
    }

    /**
     * Fail-fast self-test: certificate pinning must be configured in shipped (non-LOCAL, non-mock)
     * builds. Pinning is wired from [ApplicationConfig] pin lists, but a build/obfuscation mishap
     * could silently strip them down to empty — which would degrade to "no pinning anywhere"
     * without any visible symptom. A hard runtime assertion is cheap insurance against shipping
     * the marketplace API and update channel unprotected (issue #490). LOCAL builds talk to
     * plain-HTTP localhost where pinning doesn't apply, and the mock flavor does no networking.
     */
    private fun assertCertificatePinningEnabled(config: ApplicationConfig) {
        if (BuildConfig.FLAVOR == "mock" || BuildConfig.ENVIRONMENT == Environment.LOCAL) {
            return
        }

        val missing =
            buildList {
                if (config.marketplaceApiSSLPins.isEmpty()) add("marketplaceApiSSLPins")
                if (config.downloadsSSLPins.isEmpty()) add("downloadsSSLPins")
            }

        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "Certificate pinning is not configured for a ${BuildConfig.ENVIRONMENT.value} build " +
                    "(missing: ${missing.joinToString(", ")}). Refusing to start with the marketplace " +
                    "API and update channel unprotected.",
            )
        }
    }

    private fun getFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(FILE_HASH_BUFFER_SIZE)
        file.inputStream().use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

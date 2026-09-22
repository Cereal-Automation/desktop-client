package com.cereal.client.smoke

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.infrastructure.bootstrap.BootstrapPreferenceKey
import com.cereal.client.infrastructure.bootstrap.BootstrapPreferences
import com.cereal.client.infrastructure.data.datasource.database.KeyValueDataSource
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.ErrorResponse
import com.cereal.client.infrastructure.data.datasource.network.models.LatestAppVersionJsonResponse
import com.cereal.client.infrastructure.data.datasource.network.security.CertificatePinnerFactory
import com.cereal.client.infrastructure.data.datasource.network.security.ReleaseMetadataVerifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * The individual smoke probes exercised by [SmokeTest] against the obfuscated, prod-flavor build.
 * Each returns a [ProbeOutcome]; caught throwables are routed through [SmokeFailureClassifier] so an
 * environmental hiccup (unreachable backend) never masquerades as a build failure and a real
 * obfuscation breakage always blocks the release.
 */
@OptIn(KoinInternalApi::class)
object SmokeProbes {
    private const val NETWORK_TIMEOUT_SECONDS = 15L
    private const val RSA_2048_SIGNATURE_BYTES = 256
    private const val SHA256_HEX_LENGTH = 64

    // Bindings whose construction is heavy or side-effectful (launch Chrome, open native bridges).
    // The auto-enumerating DI probe skips them so the smoke run stays fast and truly headless; their
    // classes are still kept/loaded, just not instantiated here.
    private val DI_DENYLIST = listOf("kcef", "browser", "kdriver", "chrome", "webview", "discordrpc")

    /**
     * Resolves every root-scope Koin definition, forcing the DB / network / licensing / update
     * subsystems to actually construct under obfuscation. New singleton bindings are covered
     * automatically. A construction failure blocks the release *only* when it is a class-load / link
     * failure (the obfuscation signature); a binding that merely needs runtime parameters, or that
     * fails for a benign reason in the throwaway smoke environment, is skipped.
     *
     * Catching [Throwable] is deliberate: a binding broken by obfuscation throws `LinkageError`/
     * `Error` (not `Exception`), and that is exactly the signal this probe must detect.
     */
    @Suppress("TooGenericExceptionCaught")
    fun resolveRootDefinitions(koin: Koin): ProbeOutcome {
        val rootQualifier = koin.scopeRegistry.rootScope.scopeQualifier
        val definitions =
            koin.instanceRegistry.instances.values
                .map { it.beanDefinition }
                .filter { it.scopeQualifier == rootQualifier }
                .distinctBy { it.primaryType to it.qualifier }

        var resolved = 0
        var skipped = 0
        val failures = mutableListOf<String>()

        for (definition in definitions) {
            val typeName = definition.primaryType.qualifiedName ?: definition.primaryType.java.name
            if (DI_DENYLIST.any { typeName.lowercase().contains(it) }) {
                skipped++
                continue
            }
            try {
                koin.get<Any>(definition.primaryType, definition.qualifier, null)
                resolved++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (SmokeFailureClassifier.containsLinkageFailure(e)) {
                    failures.add("$typeName -> ${e.message ?: e::class.simpleName}")
                } else {
                    skipped++
                }
            }
        }

        return if (failures.isNotEmpty()) {
            ProbeOutcome.Failed(
                "${failures.size} binding(s) failed to construct under obfuscation: " +
                    failures.joinToString("; "),
            )
        } else {
            ProbeOutcome.Pass("resolved $resolved root binding(s), skipped $skipped")
        }
    }

    /**
     * Opens the real Room database and runs one query. This forces the bundled-SQLite native driver
     * to load and execute — the path that threw `NoSuchMethodError: nativeThreadSafeMode` once the
     * mock flavor's in-memory fakes were swapped for the real Room stack under obfuscation.
     */
    fun exerciseDatabase(koin: Koin): ProbeOutcome =
        runProbe {
            val keyValue = koin.get<KeyValueDataSource>()
            runBlocking { keyValue.getStringByKey("__cereal_smoke_probe__").first() }
            ProbeOutcome.Pass("opened the database and ran a query")
        }

    /**
     * Builds a certificate-pinned OkHttp client and makes one real request to the marketplace host,
     * exercising okio + TLS + certificate pinning end-to-end (the `okio VerifyError` / OkHttp
     * `NoClassDefFoundError` paths). Any HTTP response means the fragile stack loaded and TLS +
     * pinning succeeded; a plain connectivity failure is environmental (the code already ran); only a
     * pinning rejection against our own host blocks the release.
     */
    fun exerciseNetwork(koin: Koin): ProbeOutcome =
        runProbe {
            val config = koin.get<ApplicationConfig>()
            val pinner = CertificatePinnerFactory.create(config.marketplaceBaseUrl, config.marketplaceApiSSLPins)
            val client =
                OkHttpClient
                    .Builder()
                    .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .apply { pinner?.let { certificatePinner(it) } }
                    .build()
            val request = Request.Builder().url(config.marketplaceBaseUrl).build()
            client.newCall(request).execute().use { response ->
                ProbeOutcome.Pass("HTTP ${response.code} from marketplace host (TLS + pinning traversed)")
            }
        }

    /**
     * Exercises the pre-DI [BootstrapPreferences] store. Runs *before* `App.initialize()`, which is
     * the whole point: this store exists so telemetry consent can be read before Koin and the
     * encrypted database exist, and a probe that ran after boot would prove nothing about that.
     *
     * Round-trips a value and restores it. The store must also be resilient rather than fatal, so a
     * failed write is reported as a probe failure instead of being allowed to throw into bootstrap.
     */
    fun exerciseBootstrapPreferences(): ProbeOutcome =
        runProbe {
            val key = BootstrapPreferenceKey.CrashReportingEnabled
            val original = BootstrapPreferences.default.get(key)
            val flipped = !original

            if (!BootstrapPreferences.default.set(key, flipped)) {
                return@runProbe ProbeOutcome.Failed("bootstrap preference write failed")
            }
            val readBack = BootstrapPreferences.default.get(key)
            BootstrapPreferences.default.set(key, original)

            if (readBack == flipped) {
                ProbeOutcome.Pass("round-tripped a bootstrap preference before DI")
            } else {
                ProbeOutcome.Failed("bootstrap preference read back as $readBack, expected $flipped")
            }
        }

    /**
     * Round-trips a `@Serializable` marketplace DTO through kotlinx-serialization using its generated
     * serializer. If obfuscation stripped or mangled the generated serializer, resolving
     * [ErrorResponse.serializer] throws a class-load failure that blocks the release.
     */
    fun exerciseSerialization(): ProbeOutcome =
        runProbe {
            val json = Json { ignoreUnknownKeys = true }
            val original = ErrorResponse(message = "smoke", errors = listOf("a", "b"), status = "ok")
            val encoded = json.encodeToString(ErrorResponse.serializer(), original)
            val decoded = json.decodeFromString(ErrorResponse.serializer(), encoded)
            if (decoded == original) {
                ProbeOutcome.Pass("round-tripped ErrorResponse")
            } else {
                ProbeOutcome.Failed("serialization round-trip mismatch: $decoded != $original")
            }
        }

    /**
     * Exercises the release-metadata signature-verification path under obfuscation. First parses the
     * shipped [ApplicationConfig.releasePublicKey] to catch a stripped / corrupted key from a bad
     * build. Then runs the app's real [ReleaseMetadataVerifier] over a [LatestAppVersionJsonResponse]
     * carrying a well-formed but invalid signature: it must verify to `false` (not throw), which
     * proves the verifier class, the serializable metadata model, the embedded key, and the RSA/
     * SHA-256 machinery all survived obfuscation.
     */
    fun exerciseReleaseSignatureCrypto(koin: Koin): ProbeOutcome =
        runProbe {
            val config = koin.get<ApplicationConfig>()
            parseRsaPublicKey(config.releasePublicKey)

            val verifier = ReleaseMetadataVerifier(config.releasePublicKey)
            val invalidlySignedMetadata =
                LatestAppVersionJsonResponse(
                    version = "0.0.0",
                    minVersion = "0.0.0",
                    downloadUrl = "https://example.invalid/app",
                    downloadSha256 = "0".repeat(SHA256_HEX_LENGTH),
                    downloadSignature = Base64.getEncoder().encodeToString(ByteArray(RSA_2048_SIGNATURE_BYTES)),
                )

            if (verifier.verify(invalidlySignedMetadata)) {
                ProbeOutcome.Failed("release verifier accepted an invalid signature")
            } else {
                ProbeOutcome.Pass("parsed the release public key and ran the real signature verifier")
            }
        }

    private fun parseRsaPublicKey(pem: String): PublicKey {
        val sanitized =
            pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")
        val keyBytes = Base64.getDecoder().decode(sanitized)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
    }

    // Catching Throwable is deliberate: obfuscation breakage surfaces as LinkageError/Error, which
    // the classifier maps to a blocking result instead of letting it abort the whole smoke run.
    @Suppress("TooGenericExceptionCaught")
    private inline fun runProbe(block: () -> ProbeOutcome): ProbeOutcome =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            SmokeFailureClassifier.classify(e)
        }
}

package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.domain.model.OperatingSystemType
import com.cereal.client.infrastructure.data.datasource.network.exception.ApiException
import com.cereal.client.infrastructure.data.datasource.network.exception.NetworkException
import com.cereal.client.infrastructure.data.datasource.network.models.LatestAppVersionJsonResponse
import com.cereal.client.infrastructure.data.datasource.network.security.CertificatePinnerFactory
import com.cereal.client.infrastructure.data.datasource.network.security.ReleaseMetadataVerifier
import com.cereal.client.infrastructure.data.datasource.network.util.await
import com.cereal_automation.cereal_client.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class DownloadsApiClient(
    private val baseUrl: String,
    publicKey: String,
    enableLogging: Boolean = false,
    // SPKI pins (`sha256/<base64>`) for the downloads host. Empty disables pinning (tests/mock).
    sslPins: List<String> = emptyList(),
    // Path segment under [baseUrl] that holds the update metadata: "client" for stock Cereal, the
    // Brand's feed path for a white-label build, so a branded app only ever sees its own releases
    // and is never offered the stock installer (see docs/adr/0003).
    private val feedPath: String = "client",
) {
    private val logger = LoggerFactory.getLogger(DownloadsApiClient::class.java)
    private val logging = HttpLoggingInterceptor { message -> logger.debug(message) }
    private val client: OkHttpClient
    private val json = Json { ignoreUnknownKeys = true }
    private val releaseMetadataVerifier = ReleaseMetadataVerifier(publicKey)

    init {
        // BASIC logs only the request/response line (method, URL, status, size) — never headers or
        // bodies — avoiding capture of any signature headers or response payloads. Header redactions
        // are belt-and-suspenders in case the level is ever raised for debugging.
        logging.level = if (enableLogging) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        logging.redactHeader("Authorization")
        logging.redactHeader("X-Signature")
        logging.redactHeader("X-Salt")

        val builder =
            OkHttpClient
                .Builder()
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(logging)

        // Pin the downloads host so a compromised CA can't MITM the update channel.
        CertificatePinnerFactory.create(baseUrl, sslPins)?.let { builder.certificatePinner(it) }

        client = builder.build()
    }

    suspend fun getLatestAvailableVersionInfo(operatingSystemType: OperatingSystemType): LatestAppVersionJsonResponse {
        val suffix =
            when (operatingSystemType) {
                OperatingSystemType.Linux -> {
                    "linux"
                }

                OperatingSystemType.MacOS -> {
                    if (BuildConfig.IS_STORE_BUILD) {
                        "macos-store"
                    } else {
                        // Detect architecture for non-store macOS builds
                        val osArch = System.getProperty("os.arch") ?: ""
                        val archSuffix =
                            when {
                                osArch.contains("aarch64") || osArch.contains("arm") -> "-arm64"
                                osArch.contains("x86") || osArch.contains("amd64") -> "-x64"
                                else -> "-arm64" // fallback to ARM64 (Apple Silicon is now standard)
                            }
                        "macos$archSuffix"
                    }
                }

                OperatingSystemType.Windows -> {
                    if (BuildConfig.IS_STORE_BUILD) "windows-store" else "windows"
                }
            }

        val request =
            Request
                .Builder()
                .url("$baseUrl${feedPath.trim('/')}/latest-$suffix.json")
                .build()

        val response = client.newCall(request).await()
        val versionInfo = handleResponse<LatestAppVersionJsonResponse>(response)

        // Reject unsigned or tampered release metadata before the client downloads and launches an
        // installer from it (#484). Store builds carry no installer to verify — they update through
        // the OS store — so verification only applies when a direct download URL is present.
        if (versionInfo.downloadUrl.isNotBlank() && !releaseMetadataVerifier.verify(versionInfo)) {
            throw NetworkException("Release metadata signature verification failed.")
        }

        return versionInfo
    }

    @Throws(ApiException::class)
    private inline fun <reified T> handleResponse(response: Response): T {
        if (response.isSuccessful) {
            response.body.string().let { responseBody ->
                return json.decodeFromString<T>(responseBody)
            }
        } else {
            throw NetworkException()
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 30L
    }
}

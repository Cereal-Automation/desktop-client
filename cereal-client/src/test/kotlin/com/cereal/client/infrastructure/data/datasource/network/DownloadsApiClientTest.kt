package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.domain.model.OperatingSystemType
import com.cereal.client.infrastructure.data.datasource.network.exception.NetworkException
import com.cereal.client.infrastructure.data.datasource.network.models.LatestAppVersionJsonResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.concurrent.TimeUnit

class DownloadsApiClientTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var dataSource: DownloadsApiClient
    private lateinit var keyPair: KeyPair

    @BeforeEach
    fun setUp() {
        keyPair =
            KeyPairGenerator
                .getInstance("RSA")
                .apply { initialize(RSA_KEY_SIZE) }
                .generateKeyPair()

        val publicKeyPem =
            "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getEncoder().encodeToString(keyPair.public.encoded) +
                "\n-----END PUBLIC KEY-----"

        mockWebServer = MockWebServer()
        mockWebServer.start()
        val baseUrl = mockWebServer.url("").toString()
        dataSource = DownloadsApiClient(baseUrl, publicKeyPem, enableLogging = false)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    /** Signs the canonical release message exactly as [ReleaseMetadataVerifier] expects. */
    private fun sign(
        version: String,
        minVersion: String,
        downloadUrl: String,
        sha256: String,
    ): String {
        val message = "$version|$minVersion|$downloadUrl|$sha256"
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(message.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    /** Builds a fully-signed metadata JSON body. */
    private fun signedJson(
        version: String,
        minVersion: String,
        downloadUrl: String,
        sha256: String = DEFAULT_SHA256,
        signature: String = sign(version, minVersion, downloadUrl, sha256),
    ): String =
        """{"version":"$version","min_version":"$minVersion","download_url":"$downloadUrl",""" +
            """"download_sha256":"$sha256","download_signature":"$signature"}"""

    @Test
    fun `getLatestAvailableVersionInfo should return correct response for Linux`() =
        runTest {
            // Arrange
            val expectedResponse =
                LatestAppVersionJsonResponse(
                    version = "2.0.0",
                    minVersion = "1.5.0",
                    downloadUrl = "https://example.com/linux-download",
                )
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(signedJson("2.0.0", "1.5.0", "https://example.com/linux-download"))
                    .setBodyDelay(50, TimeUnit.MILLISECONDS),
            )

            // Act
            val result = dataSource.getLatestAvailableVersionInfo(OperatingSystemType.Linux)

            // Assert
            assertEquals(expectedResponse.version, result.version)
            assertEquals(expectedResponse.minVersion, result.minVersion)
            assertEquals(expectedResponse.downloadUrl, result.downloadUrl)

            val request = mockWebServer.takeRequest()
            assertEquals("/client/latest-linux.json", request.path)
            assertEquals("GET", request.method)
        }

    @Test
    fun `getLatestAvailableVersionInfo should return correct response for MacOS with architecture detection`() =
        runTest {
            // Arrange
            val expectedResponse =
                LatestAppVersionJsonResponse(
                    version = "2.1.0",
                    minVersion = "2.0.0",
                    downloadUrl = "https://example.com/macos-download",
                )
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(signedJson("2.1.0", "2.0.0", "https://example.com/macos-download")),
            )

            // Act
            val result = dataSource.getLatestAvailableVersionInfo(OperatingSystemType.MacOS)

            // Assert
            assertEquals(expectedResponse.version, result.version)
            assertEquals(expectedResponse.minVersion, result.minVersion)
            assertEquals(expectedResponse.downloadUrl, result.downloadUrl)

            // Verify the correct architecture-specific JSON is fetched
            val request = mockWebServer.takeRequest()
            val osArch = System.getProperty("os.arch") ?: ""
            val expectedPath =
                when {
                    osArch.contains("aarch64") || osArch.contains("arm") -> "/client/latest-macos-arm64.json"
                    osArch.contains("x86") || osArch.contains("amd64") -> "/client/latest-macos-x64.json"
                    else -> "/client/latest-macos-arm64.json" // fallback to ARM64
                }
            assertEquals(expectedPath, request.path)
        }

    @Test
    fun `getLatestAvailableVersionInfo should return correct response for Windows`() =
        runTest {
            // Arrange
            val expectedResponse =
                LatestAppVersionJsonResponse(
                    version = "2.2.0",
                    minVersion = "2.1.0",
                    downloadUrl = "https://example.com/windows-download",
                )
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(signedJson("2.2.0", "2.1.0", "https://example.com/windows-download")),
            )

            // Act
            val result = dataSource.getLatestAvailableVersionInfo(OperatingSystemType.Windows)

            // Assert
            assertEquals(expectedResponse.version, result.version)
            assertEquals(expectedResponse.minVersion, result.minVersion)
            assertEquals(expectedResponse.downloadUrl, result.downloadUrl)

            val request = mockWebServer.takeRequest()
            assertEquals("/client/latest-windows.json", request.path)
        }

    @Test
    fun `getLatestAvailableVersionInfo should reject metadata without a signature`() =
        runTest {
            // Arrange — valid JSON but no signature/hash fields.
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"version":"2.0.0","min_version":"1.5.0","download_url":"https://example.com/x"}"""),
            )

            // Act & Assert
            assertThrows<NetworkException> {
                dataSource.getLatestAvailableVersionInfo(OperatingSystemType.Linux)
            }
        }

    @Test
    fun `getLatestAvailableVersionInfo should reject metadata with an invalid signature`() =
        runTest {
            // Arrange — signature is well-formed Base64 but not a valid signature for the message.
            val bogusSignature = Base64.getEncoder().encodeToString(ByteArray(RSA_SIGNATURE_BYTES))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        signedJson(
                            "2.0.0",
                            "1.5.0",
                            "https://example.com/x",
                            signature = bogusSignature,
                        ),
                    ),
            )

            // Act & Assert
            assertThrows<NetworkException> {
                dataSource.getLatestAvailableVersionInfo(OperatingSystemType.Linux)
            }
        }

    @Test
    fun `getLatestAvailableVersionInfo should reject metadata when the download url was tampered with`() =
        runTest {
            // Arrange — sign the original URL, then serve a different one (MITM rewriting the target).
            val signature = sign("2.0.0", "1.5.0", "https://trusted.example.com/installer", DEFAULT_SHA256)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        signedJson(
                            "2.0.0",
                            "1.5.0",
                            "https://evil.example.com/installer",
                            signature = signature,
                        ),
                    ),
            )

            // Act & Assert
            assertThrows<NetworkException> {
                dataSource.getLatestAvailableVersionInfo(OperatingSystemType.Linux)
            }
        }

    @Test
    fun `getLatestAvailableVersionInfo should accept unsigned store metadata without a download url`() =
        runTest {
            // Arrange — store builds carry a store_url and an empty download_url, so no installer is
            // downloaded and signature verification does not apply.
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """{"version":"2.0.0","min_version":"1.5.0","download_url":"","store_url":"https://store.example.com/app"}""",
                    ),
            )

            // Act
            val result = dataSource.getLatestAvailableVersionInfo(OperatingSystemType.Windows)

            // Assert
            assertEquals("2.0.0", result.version)
            assertEquals("https://store.example.com/app", result.storeUrl)
        }

    @Test
    fun `getLatestAvailableVersionInfo should throw NetworkException on HTTP 404`() =
        runTest {
            // Arrange
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setBody("Not Found"),
            )

            // Act & Assert
            assertThrows<NetworkException> {
                dataSource.getLatestAvailableVersionInfo(OperatingSystemType.Linux)
            }
        }

    @Test
    fun `getLatestAvailableVersionInfo should throw NetworkException on HTTP 500`() =
        runTest {
            // Arrange
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("Internal Server Error"),
            )

            // Act & Assert
            assertThrows<NetworkException> {
                dataSource.getLatestAvailableVersionInfo(OperatingSystemType.MacOS)
            }
        }

    @Test
    fun `getLatestAvailableVersionInfo should throw SerializationException on malformed JSON`() =
        runTest {
            // Arrange
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"invalid": "json", "missing": "required_fields"}"""),
            )

            // Act & Assert
            assertThrows<SerializationException> {
                dataSource.getLatestAvailableVersionInfo(OperatingSystemType.Windows)
            }
        }

    @Test
    fun `getLatestAvailableVersionInfo should throw SerializationException on empty response body`() =
        runTest {
            // Arrange
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(""),
            )

            // Act & Assert
            assertThrows<SerializationException> {
                dataSource.getLatestAvailableVersionInfo(OperatingSystemType.Linux)
            }
        }

    @Test
    fun `getLatestAvailableVersionInfo should handle response with null values gracefully`() =
        runTest {
            // Arrange
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""null"""),
            )

            // Act & Assert
            assertThrows<SerializationException> {
                dataSource.getLatestAvailableVersionInfo(OperatingSystemType.MacOS)
            }
        }

    @Test
    fun `getLatestAvailableVersionInfo should throw SerializationException on partial JSON response`() =
        runTest {
            // Arrange - Missing download_url field
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"version":"1.0.0","min_version":"1.0.0"}"""),
            )

            // Act & Assert
            assertThrows<SerializationException> {
                dataSource.getLatestAvailableVersionInfo(OperatingSystemType.Windows)
            }
        }

    @Test
    fun `getLatestAvailableVersionInfo should handle special characters in JSON response`() =
        runTest {
            // Arrange
            val version = "1.0.0-beta+build.123"
            val url = "https://example.com/file with spaces & symbols.zip"
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(signedJson(version, "1.0.0", url)),
            )

            // Act
            val result = dataSource.getLatestAvailableVersionInfo(OperatingSystemType.Linux)

            // Assert
            assertEquals(version, result.version)
            assertEquals("1.0.0", result.minVersion)
            assertEquals(url, result.downloadUrl)
        }

    @Test
    fun `getLatestAvailableVersionInfo should handle very large JSON response`() =
        runTest {
            // Arrange
            val largeUrl = "https://example.com/" + "a".repeat(1000) + ".zip"
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(signedJson("1.0.0", "1.0.0", largeUrl)),
            )

            // Act
            val result = dataSource.getLatestAvailableVersionInfo(OperatingSystemType.MacOS)

            // Assert
            assertEquals("1.0.0", result.version)
            assertEquals("1.0.0", result.minVersion)
            assertEquals(largeUrl, result.downloadUrl)
        }

    @Test
    fun `getLatestAvailableVersionInfo should handle HTTP 401 Unauthorized`() =
        runTest {
            // Arrange
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody("Unauthorized"),
            )

            // Act & Assert
            assertThrows<NetworkException> {
                dataSource.getLatestAvailableVersionInfo(OperatingSystemType.Windows)
            }
        }

    @Test
    fun `getLatestAvailableVersionInfo should handle HTTP 403 Forbidden`() =
        runTest {
            // Arrange
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(403)
                    .setBody("Forbidden"),
            )

            // Act & Assert
            assertThrows<NetworkException> {
                dataSource.getLatestAvailableVersionInfo(OperatingSystemType.Linux)
            }
        }

    @Test
    fun `getLatestAvailableVersionInfo should handle HTTP 503 Service Unavailable`() =
        runTest {
            // Arrange
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(503)
                    .setBody("Service Unavailable"),
            )

            // Act & Assert
            assertThrows<NetworkException> {
                dataSource.getLatestAvailableVersionInfo(OperatingSystemType.MacOS)
            }
        }

    @Test
    fun `getLatestAvailableVersionInfo should throw SerializationException on invalid JSON syntax`() =
        runTest {
            // Arrange
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    // Missing closing quote and brace
                    .setBody("""{"version":"1.0.0","min_version":"1.0.0","download_url":"""),
            )

            // Act & Assert
            assertThrows<SerializationException> {
                dataSource.getLatestAvailableVersionInfo(OperatingSystemType.Windows)
            }
        }

    @Test
    fun `getLatestAvailableVersionInfo should handle JSON with extra fields`() =
        runTest {
            // Arrange
            val url = "https://example.com/test.zip"
            val signature = sign("1.0.0", "1.0.0", url, DEFAULT_SHA256)
            val jsonWithExtraFields =
                """{"version":"1.0.0","min_version":"1.0.0","download_url":"$url",""" +
                    """"download_sha256":"$DEFAULT_SHA256","download_signature":"$signature",""" +
                    """"extra_field":"ignored","another_field":123}"""
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(jsonWithExtraFields),
            )

            // Act
            val result = dataSource.getLatestAvailableVersionInfo(OperatingSystemType.Linux)

            // Assert - Should ignore extra fields and parse successfully
            assertEquals("1.0.0", result.version)
            assertEquals("1.0.0", result.minVersion)
            assertEquals(url, result.downloadUrl)
        }

    @Test
    fun `getLatestAvailableVersionInfo uses the brand feed path when configured`() =
        runTest {
            val publicKeyPem =
                "-----BEGIN PUBLIC KEY-----\n" +
                    Base64.getEncoder().encodeToString(keyPair.public.encoded) +
                    "\n-----END PUBLIC KEY-----"
            val brandedClient =
                DownloadsApiClient(
                    baseUrl = mockWebServer.url("").toString(),
                    publicKey = publicKeyPem,
                    enableLogging = false,
                    feedPath = "ecommerce",
                )
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(signedJson("2.0.0", "1.5.0", "https://example.com/linux-download")),
            )

            brandedClient.getLatestAvailableVersionInfo(OperatingSystemType.Linux)

            val request = mockWebServer.takeRequest()
            assertEquals("/ecommerce/latest-linux.json", request.path)
        }

    private companion object {
        const val RSA_KEY_SIZE = 2048
        const val RSA_SIGNATURE_BYTES = 256
        const val DEFAULT_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}

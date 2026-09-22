package com.cereal.client.infrastructure.data.datasource.network

import FileDownloader
import com.cereal.client.application.exception.CorruptDownloadException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertFailsWith

class FileDownloaderTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var destination: File

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        destination = Files.createTempFile("cereal-test-download", ".bin").toFile()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
        destination.delete()
    }

    @Test
    fun `download should stream body to destination file`() =
        runTest {
            val content = "hello cereal installer"
            mockWebServer.enqueue(
                MockResponse()
                    .setBody(content)
                    .addHeader("Content-Length", content.length.toString()),
            )

            val events = FileDownloader().download(mockWebServer.url("/update.dmg").toString(), destination).toList()

            assertTrue(destination.exists(), "destination file should exist after download")
            assertEquals(content, destination.readText())
            assertTrue(events.any { it is FileDownloader.DownloadProgress.Finished })
            val finished = events.filterIsInstance<FileDownloader.DownloadProgress.Finished>().single()
            assertEquals(destination, finished.file)
        }

    @Test
    fun `download should emit Downloading progress events when Content-Length is known`() =
        runTest {
            val content = "a".repeat(1024)
            mockWebServer.enqueue(
                MockResponse()
                    .setBody(content)
                    .addHeader("Content-Length", content.length.toString()),
            )

            val events = FileDownloader().download(mockWebServer.url("/update.dmg").toString(), destination).toList()

            assertTrue(
                events.any { it is FileDownloader.DownloadProgress.Downloading },
                "should emit at least one Downloading event",
            )
        }

    @Test
    fun `download should reject response when Content-Length exceeds maxSizeBytes`() =
        runTest {
            val content = "large content"
            mockWebServer.enqueue(
                MockResponse()
                    .setBody(content)
                    .addHeader("Content-Length", "100"),
            )

            assertFailsWith<IOException> {
                FileDownloader()
                    .download(mockWebServer.url("/update.dmg").toString(), destination, maxSizeBytes = 50L)
                    .toList()
            }

            // Destination was never opened — it contains no content from the download
            assertEquals(0L, destination.length(), "destination file should not have been written to")
        }

    @Test
    fun `download should abort and throw IOException when body exceeds maxSizeBytes`() =
        runTest {
            // No Content-Length header — size cap enforced during copy
            val content = "a".repeat(200)
            mockWebServer.enqueue(MockResponse().setBody(content))

            assertFailsWith<IOException> {
                FileDownloader()
                    .download(mockWebServer.url("/update.dmg").toString(), destination, maxSizeBytes = 100L)
                    .toList()
            }
        }

    @Test
    fun `download should close with IOException on non-2xx response`() =
        runTest {
            mockWebServer.enqueue(MockResponse().setResponseCode(404))

            assertFailsWith<IOException> {
                FileDownloader().download(mockWebServer.url("/update.dmg").toString(), destination).toList()
            }
        }

    @Test
    fun `download should succeed when expectedSha256 matches the downloaded bytes`() =
        runTest {
            val content = "hello cereal installer"
            mockWebServer.enqueue(
                MockResponse()
                    .setBody(content)
                    .addHeader("Content-Length", content.length.toString()),
            )

            val events =
                FileDownloader()
                    .download(
                        mockWebServer.url("/update.dmg").toString(),
                        destination,
                        expectedSha256 = sha256Hex(content),
                    ).toList()

            assertEquals(content, destination.readText())
            assertTrue(events.any { it is FileDownloader.DownloadProgress.Finished })
        }

    @Test
    fun `download should reject and delete the file when expectedSha256 does not match`() =
        runTest {
            val content = "tampered installer payload"
            mockWebServer.enqueue(
                MockResponse()
                    .setBody(content)
                    .addHeader("Content-Length", content.length.toString()),
            )

            // Distinct type (an IOException subtype) so the interactor mapping can tell the user the
            // file is corrupt/stale and retryable, rather than surfacing a generic "check your connection".
            assertFailsWith<CorruptDownloadException> {
                FileDownloader()
                    .download(
                        mockWebServer.url("/update.dmg").toString(),
                        destination,
                        expectedSha256 = "0".repeat(64),
                    ).toList()
            }

            assertTrue(!destination.exists(), "destination file should be deleted on hash mismatch")
        }

    @Test
    fun `download should close with IOException on network failure`() =
        runTest {
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

            assertFailsWith<IOException> {
                FileDownloader().download(mockWebServer.url("/update.dmg").toString(), destination).toList()
            }
        }

    private fun sha256Hex(content: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

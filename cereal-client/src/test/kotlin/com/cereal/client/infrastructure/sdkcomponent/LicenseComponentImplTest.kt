package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.infrastructure.data.datasource.network.MarketplaceDataSource
import com.cereal.sdk.component.license.HttpResponse
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class LicenseComponentImplTest {
    private lateinit var dataSource: MarketplaceDataSource
    private lateinit var licenseComponent: LicenseComponentImpl
    private lateinit var response: Response
    private val publicScriptId = "script1"
    private val salt = "salt1"

    @BeforeEach
    fun setUp() {
        dataSource = mockk()
        licenseComponent = LicenseComponentImpl(dataSource)
        response =
            mockk {
                every { isSuccessful } returns true
                every { code } returns 200
                every { body } returns
                    mockk {
                        every { bytes() } returns "responseBody".toByteArray()
                    }
                every { close() } just Runs
            }
    }

    @Test
    fun `checkScriptLicense should return cached response if exists`() =
        runBlocking {
            // Arrange
            val expectedResponse =
                mockk<HttpResponse> {
                    every { isSuccessful } returns true
                }

            licenseComponent.cache[publicScriptId to salt] = expectedResponse

            // Act
            val result = licenseComponent.checkScriptLicense(publicScriptId, salt)

            // Assert
            assertEquals(expectedResponse, result)
            coVerify(exactly = 0) { dataSource.checkScriptLicense(any(), any()) }
        }

    @Test
    fun `checkScriptLicense should call client and cache response if not cached`() =
        runBlocking {
            // Arrange
            coEvery { dataSource.checkScriptLicense(publicScriptId, salt) } returns response

            // Act
            val result = licenseComponent.checkScriptLicense(publicScriptId, salt) as OkHttpResponse

            // Assert
            assertTrue(result.isSuccessful)
            assertEquals(200, result.code)
            assertEquals("responseBody", String(result.body()))
            coVerify(exactly = 1) { dataSource.checkScriptLicense(publicScriptId, salt) }

            // Verify cache
            assertTrue(licenseComponent.cache.containsKey(publicScriptId to salt))
            Unit
        }

    @Test
    fun `checkScriptLicense should handle IOException from client`() =
        runBlocking {
            // Arrange
            coEvery { dataSource.checkScriptLicense(publicScriptId, salt) } throws IOException("Network error")

            // Act & Assert
            try {
                licenseComponent.checkScriptLicense(publicScriptId, salt)
            } catch (e: IOException) {
                assertEquals("Network error", e.message)
            }
            coVerify(exactly = 1) { dataSource.checkScriptLicense(publicScriptId, salt) }
        }

    @Test
    fun `checkScriptLicense should remove mutex from map after execution`() =
        runBlocking {
            // Arrange
            coEvery { dataSource.checkScriptLicense(publicScriptId, salt) } returns response

            // Act
            licenseComponent.checkScriptLicense(publicScriptId, salt)

            // Assert
            assertTrue(!licenseComponent.mutexMap.containsKey(publicScriptId to salt))
            coVerify(exactly = 1) { dataSource.checkScriptLicense(publicScriptId, salt) }
        }

    @Test
    fun `checkScriptLicense should use mutex and lock correctly`() =
        runBlocking {
            // Arrange
            coEvery { dataSource.checkScriptLicense(publicScriptId, salt) } coAnswers {
                // Simulate a delay to check the mutex lock functionality
                delay(100)
                response
            }

            val results = mutableListOf<HttpResponse>()

            // Act
            val job1 =
                async {
                    licenseComponent.checkScriptLicense(publicScriptId, salt).also { results.add(it) }
                }
            val job2 =
                async {
                    licenseComponent.checkScriptLicense(publicScriptId, salt).also { results.add(it) }
                }

            awaitAll(job1, job2)

            // Assert
            assertEquals(2, results.size)
            assertEquals(results[0], results[1]) // Both jobs should return the same response (cache hit)
            coVerify(exactly = 1) { dataSource.checkScriptLicense(publicScriptId, salt) }
        }
}

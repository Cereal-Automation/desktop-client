package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.domain.model.OperatingSystemType
import com.cereal.client.infrastructure.data.datasource.network.models.LatestAppVersionJsonResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class RealObjectStorageDataSourceTest {
    private val apiClient = mockk<DownloadsApiClient>()
    private val dataSource = RealObjectStorageDataSource(apiClient)

    @Test
    fun `getLatestAvailableVersionInfo delegates to the spaces api client`() =
        runTest {
            val expected =
                LatestAppVersionJsonResponse(
                    version = "2.0.0",
                    minVersion = "1.0.0",
                    downloadUrl = "https://example.com/app",
                )
            coEvery { apiClient.getLatestAvailableVersionInfo(OperatingSystemType.Linux) } returns expected

            val result = dataSource.getLatestAvailableVersionInfo(OperatingSystemType.Linux)

            assertSame(expected, result)
            coVerify(exactly = 1) { apiClient.getLatestAvailableVersionInfo(OperatingSystemType.Linux) }
        }
}

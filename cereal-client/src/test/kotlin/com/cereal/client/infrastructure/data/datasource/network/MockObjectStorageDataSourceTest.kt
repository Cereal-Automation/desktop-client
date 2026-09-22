package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.domain.model.OperatingSystemType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MockObjectStorageDataSourceTest {
    private val dataSource = MockObjectStorageDataSource()

    @Test
    fun `getLatestAvailableVersionInfo returns the dmg installer for macOS`() =
        runTest {
            val result = dataSource.getLatestAvailableVersionInfo(OperatingSystemType.MacOS)

            assertEquals("1.9.0", result.version)
            assertEquals("1.0.0", result.minVersion)
            assertTrue(result.downloadUrl.endsWith("cereal-client-latest-arm64.dmg"))
        }

    @Test
    fun `getLatestAvailableVersionInfo returns the exe installer for Windows`() =
        runTest {
            val result = dataSource.getLatestAvailableVersionInfo(OperatingSystemType.Windows)

            assertTrue(result.downloadUrl.endsWith("cereal-client-latest.exe"))
        }

    @Test
    fun `getLatestAvailableVersionInfo returns the deb installer for Linux`() =
        runTest {
            val result = dataSource.getLatestAvailableVersionInfo(OperatingSystemType.Linux)

            assertTrue(result.downloadUrl.endsWith("cereal-client-latest.deb"))
        }
}

package com.cereal.client.infrastructure.provider

import FileDownloader
import com.cereal.client.domain.model.OperatingSystemType
import com.cereal.client.domain.model.app.DownloadStatus
import com.cereal.client.domain.model.app.Version
import com.cereal.client.fixtures.FakeObjectStorageDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemTempDataSource
import com.cereal.client.infrastructure.data.datasource.network.models.LatestAppVersionJsonResponse
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.swiftzer.semver.SemVer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class AppUpdateProviderImplTest {
    private val objectStorageDataSource = FakeObjectStorageDataSource()

    // MockK: FileDownloader is a concrete external file/network IO class with no fake.
    private val fileDownload = mockk<FileDownloader>(relaxed = true)

    // MockK: FileSystemTempDataSource resolves a real OS temp path (filesystem edge).
    private val fileSystemTempDataSource = mockk<FileSystemTempDataSource>(relaxed = true)

    private fun provider(operatingSystem: OperatingSystemType = OperatingSystemType.MacOS) =
        AppUpdateProviderImpl(
            InMemoryApplicationConfig(operatingSystem = operatingSystem),
            objectStorageDataSource,
            fileDownload,
            fileSystemTempDataSource,
        )

    private lateinit var provider: AppUpdateProviderImpl

    @BeforeEach
    fun setUp() {
        provider = provider()
    }

    @Test
    fun `getLatestAvailableAppVersion queries storage for the configured operating system`() =
        runTest {
            val windowsProvider = provider(OperatingSystemType.Windows)

            windowsProvider.getLatestAvailableAppVersion()

            assertEquals(listOf(OperatingSystemType.Windows), objectStorageDataSource.requestedOperatingSystems)
        }

    @Test
    fun `getLatestAvailableAppVersion maps the storage response into a Version`() =
        runTest {
            objectStorageDataSource.versionInfo =
                LatestAppVersionJsonResponse(
                    version = "2.1.0",
                    minVersion = "1.0.0",
                    downloadUrl = "https://downloads.test/app.dmg",
                    storeUrl = "https://store.test/app",
                    downloadSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                )

            val version = provider.getLatestAvailableAppVersion()

            assertEquals(SemVer.parse("2.1.0"), version.version)
            assertEquals(SemVer.parse("1.0.0"), version.minRequiredVersion)
            assertEquals("https://downloads.test/app.dmg", version.downloadUrl)
            assertEquals("https://store.test/app", version.storeUrl)
            assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", version.downloadSha256)
        }

    @Test
    fun `downloadVersion maps download progress events to download status`() =
        runTest {
            val destination = File("/tmp/app.dmg")
            val version =
                Version(
                    version = SemVer.parse("2.1.0"),
                    minRequiredVersion = SemVer.parse("1.0.0"),
                    downloadUrl = "https://downloads.test/app.dmg",
                    downloadSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                )
            every { fileSystemTempDataSource.resolveAppBinaryDestination(version) } returns destination
            every {
                fileDownload.download(version.downloadUrl, destination, expectedSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
            } returns
                flowOf(
                    FileDownloader.DownloadProgress.Downloading(bytesRead = 50L, contentLength = 200L),
                    FileDownloader.DownloadProgress.Finished(destination),
                )

            val statuses = provider.downloadVersion(version).toList()

            assertEquals(2, statuses.size)
            assertEquals(DownloadStatus.Downloading(25), statuses[0])
            // Finished carries the expected digest so install-time re-verification can happen.
            assertEquals(
                DownloadStatus.Finished(
                    destination,
                    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                ),
                statuses[1],
            )
        }

    @Test
    fun `downloadVersion computes percentage for the final downloading event`() =
        runTest {
            val destination = File("/tmp/app.dmg")
            val version =
                Version(
                    version = SemVer.parse("2.1.0"),
                    minRequiredVersion = SemVer.parse("1.0.0"),
                    downloadUrl = "https://downloads.test/app.dmg",
                )
            every { fileSystemTempDataSource.resolveAppBinaryDestination(version) } returns destination
            every {
                fileDownload.download(version.downloadUrl, destination, expectedSha256 = null)
            } returns
                flowOf(
                    FileDownloader.DownloadProgress.Downloading(bytesRead = 200L, contentLength = 200L),
                )

            val first = provider.downloadVersion(version).first()

            assertEquals(DownloadStatus.Downloading(100), first)
        }
}

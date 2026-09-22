package com.cereal.client.application.interactor.app

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.app.DownloadStatus
import com.cereal.client.domain.model.app.Version
import com.cereal.client.domain.model.exception.InvalidVersionException
import com.cereal.client.domain.provider.AppUpdateProvider
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.swiftzer.semver.SemVer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class DownloadLatestAppVersionInteractorTest {
    @Test
    fun `run downloads the latest available version and streams its status`() =
        runTest {
            val latest =
                Version(
                    version = SemVer(1, 2, 0),
                    minRequiredVersion = SemVer(1, 0, 0),
                    downloadUrl = "https://example/app",
                )
            val statuses =
                listOf(
                    DownloadStatus.Downloading(progress = 50),
                    DownloadStatus.Finished(File("/tmp/app")),
                )
            // Fake that records which version it was asked to download and replays statuses.
            var downloadedVersion: Version? = null
            val appUpdateProvider =
                object : AppUpdateProvider {
                    override suspend fun getLatestAvailableAppVersion(): Version = latest

                    override suspend fun downloadVersion(version: Version): Flow<DownloadStatus> {
                        downloadedVersion = version
                        return flowOf(*statuses.toTypedArray())
                    }
                }
            val interactor = DownloadLatestAppVersionInteractor(appUpdateProvider)

            val emitted = interactor.run(Interactor.None()).toList()

            assertEquals(latest, downloadedVersion)
            assertEquals(statuses, emitted)
        }

    @Test
    fun `invoke surfaces a failure instead of crashing when the latest version has no download url`() =
        runTest {
            val latest =
                Version(
                    version = SemVer(1, 2, 0),
                    minRequiredVersion = SemVer(1, 0, 0),
                    // Blank download URL: a store build or download-less metadata. Attempting to
                    // download it must not crash the app (the original "DownloadUrl cannot be blank"
                    // bug) — it should be reported as a normal interactor failure.
                    downloadUrl = "",
                )
            var downloadAttempted = false
            val appUpdateProvider =
                object : AppUpdateProvider {
                    override suspend fun getLatestAvailableAppVersion(): Version = latest

                    override suspend fun downloadVersion(version: Version): Flow<DownloadStatus> {
                        downloadAttempted = true
                        return emptyFlow()
                    }
                }
            val interactor = DownloadLatestAppVersionInteractor(appUpdateProvider)

            val emitted = interactor(Interactor.None()).toList()

            assertFalse(downloadAttempted, "should not attempt a blank-URL download")
            assertEquals(1, emitted.size)
            val failure = assertInstanceOf(SuspendableResult.Failure::class.java, emitted.single())
            assertTrue(failure.error is InvalidVersionException)
        }
}

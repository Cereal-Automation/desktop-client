package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.model.OpenFileResult
import com.cereal.client.domain.model.app.UpdateInstallResult
import com.cereal.client.domain.provider.SystemProvider
import java.io.File

/**
 * In-memory [SystemProvider] — avoids touching the OS (tray icon, browser, file opening) in tests.
 * Records the URLs and files it was asked to open so interactor tests can assert on real state
 * instead of verifying mock calls.
 */
class InMemorySystemProvider : SystemProvider {
    val browsedUrls = mutableListOf<String>()
    val openedFiles = mutableListOf<File>()
    val installedUpdates = mutableListOf<File>()

    /** Expected SHA-256 passed with each [installUpdate] call, parallel to [installedUpdates]. */
    val installedUpdateSha256s = mutableListOf<String?>()

    /** Result returned by [installUpdate]; tests can flip this to simulate a self-install relaunch. */
    var installUpdateResult: UpdateInstallResult = UpdateInstallResult.Opened

    /** Result returned by [open]; override to exercise the reveal/failure fallbacks. */
    var openResult: OpenFileResult = OpenFileResult.Opened

    override suspend fun createTrayIcon() = Unit

    override suspend fun browser(url: String) {
        browsedUrls += url
    }

    override suspend fun open(directory: File): OpenFileResult {
        openedFiles += directory
        return openResult
    }

    override suspend fun installUpdate(
        installer: File,
        expectedSha256: String?,
    ): UpdateInstallResult {
        installedUpdates += installer
        installedUpdateSha256s += expectedSha256
        return installUpdateResult
    }
}

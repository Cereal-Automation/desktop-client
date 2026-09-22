package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.model.app.DownloadStatus
import com.cereal.client.domain.model.app.Version
import com.cereal.client.domain.provider.AppUpdateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import net.swiftzer.semver.SemVer

/**
 * In-memory app-update provider used in the `mock` flavor so version-check logic works without a
 * live object-storage endpoint. Reports no available update so the update banner is never shown
 * during local development.
 */
class InMemoryAppUpdateProvider : AppUpdateProvider {
    private val installedVersion = SemVer(1, 0, 0)

    override suspend fun getLatestAvailableAppVersion(): Version =
        Version(
            version = installedVersion,
            minRequiredVersion = installedVersion,
            downloadUrl = "https://example.com/app",
        )

    override suspend fun downloadVersion(version: Version): Flow<DownloadStatus> = emptyFlow()
}

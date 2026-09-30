package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.model.app.DownloadStatus
import com.cereal.client.domain.model.app.Version
import com.cereal.client.domain.provider.AppUpdateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import net.swiftzer.semver.SemVer

/**
 * In-memory app-update provider used in the `mock` flavor so version-check logic works without a
 * live object-storage endpoint. Reports no available update by default (the installed version is
 * 1.0.0) so the update banner is never shown during local development. Tests set [latestVersion]
 * to simulate an available or required update and read [downloads] to check nothing was fetched.
 */
class InMemoryAppUpdateProvider : AppUpdateProvider {
    @Volatile
    var latestVersion: Version = version("1.0.0")

    val downloads = mutableListOf<Version>()

    override suspend fun getLatestAvailableAppVersion(): Version = latestVersion

    override suspend fun downloadVersion(version: Version): Flow<DownloadStatus> {
        downloads += version
        return emptyFlow()
    }

    companion object {
        fun version(
            latest: String,
            minRequired: String = "1.0.0",
        ) = Version(
            version = SemVer.parse(latest),
            minRequiredVersion = SemVer.parse(minRequired),
            downloadUrl = "https://example.com/app",
        )
    }
}

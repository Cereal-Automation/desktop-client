package com.cereal.client.infrastructure.provider

import FileDownloader
import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.app.DownloadStatus
import com.cereal.client.domain.model.app.Version
import com.cereal.client.domain.provider.AppUpdateProvider
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemTempDataSource
import com.cereal.client.infrastructure.data.datasource.network.ObjectStorageDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import net.swiftzer.semver.SemVer

class AppUpdateProviderImpl(
    private val applicationConfig: ApplicationConfig,
    private val objectStorageDataSource: ObjectStorageDataSource,
    private val fileDownload: FileDownloader,
    private val fileSystemTempDataSource: FileSystemTempDataSource,
) : AppUpdateProvider {
    override suspend fun getLatestAvailableAppVersion(): Version {
        val latestVersionJson =
            objectStorageDataSource.getLatestAvailableVersionInfo(applicationConfig.operatingSystem)

        return Version(
            SemVer.parse(latestVersionJson.version),
            SemVer.parse(latestVersionJson.minVersion),
            latestVersionJson.downloadUrl,
            latestVersionJson.storeUrl,
            latestVersionJson.downloadSha256,
        )
    }

    override suspend fun downloadVersion(version: Version): Flow<DownloadStatus> {
        val destination = fileSystemTempDataSource.resolveAppBinaryDestination(version)
        return fileDownload.download(version.downloadUrl, destination, expectedSha256 = version.downloadSha256).transform {
            if (it is FileDownloader.DownloadProgress.Downloading) {
                emit(DownloadStatus.Downloading((PERCENT_MULTIPLIER * it.bytesRead / it.contentLength).toInt()))
            } else if (it is FileDownloader.DownloadProgress.Finished) {
                // Carry the expected digest along so the installer can be re-verified at install
                // time, not just while streaming the download.
                emit(DownloadStatus.Finished(it.file, version.downloadSha256))
            }
        }
    }

    private companion object {
        private const val PERCENT_MULTIPLIER = 100
    }
}

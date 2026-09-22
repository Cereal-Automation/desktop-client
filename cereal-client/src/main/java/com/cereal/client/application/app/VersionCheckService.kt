package com.cereal.client.application.app

import com.cereal.client.domain.model.app.Version
import com.cereal.client.domain.provider.AppUpdateProvider
import com.cereal.client.domain.repository.ApplicationRepository

sealed class UpdateCheckResult {
    object UpToDate : UpdateCheckResult()

    data class UpdateAvailable(
        val version: Version,
    ) : UpdateCheckResult()

    data class UpdateRequired(
        val version: Version,
    ) : UpdateCheckResult()
}

class VersionCheckService(
    private val applicationRepository: ApplicationRepository,
    private val appUpdateProvider: AppUpdateProvider,
) {
    suspend fun checkForUpdates(): UpdateCheckResult {
        val latestVersion = appUpdateProvider.getLatestAvailableAppVersion()
        val installedVersion = applicationRepository.getInstalledVersion()

        val isUpdateRequired = installedVersion < latestVersion.minRequiredVersion
        val isUpdateAvailable = installedVersion < latestVersion.version

        return when {
            isUpdateRequired -> UpdateCheckResult.UpdateRequired(latestVersion)
            isUpdateAvailable -> UpdateCheckResult.UpdateAvailable(latestVersion)
            else -> UpdateCheckResult.UpToDate
        }
    }
}

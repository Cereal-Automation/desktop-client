package com.cereal.client.application.interactor.app

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.app.DownloadStatus
import com.cereal.client.domain.model.exception.InvalidVersionException
import com.cereal.client.domain.provider.AppUpdateProvider
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@OptIn(FlowPreview::class)
class DownloadLatestAppVersionInteractor(
    private val appUpdateProvider: AppUpdateProvider,
) : FlowInteractor<DownloadStatus, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<DownloadStatus> {
        val latestVersion = appUpdateProvider.getLatestAvailableAppVersion()
        if (latestVersion.downloadUrl.isBlank()) {
            // No direct installer for this version (e.g. a store-distributed build, which should
            // update via its store URL instead). Fail clearly rather than attempting a blank-URL
            // download. Surfaced as an error by FlowInteractor; never reaches the user as a crash.
            throw InvalidVersionException("No direct download is available for this version.")
        }
        return appUpdateProvider.downloadVersion(latestVersion)
    }
}

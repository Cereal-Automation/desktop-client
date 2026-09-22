package com.cereal.client.domain.provider

import com.cereal.client.domain.model.app.DownloadStatus
import com.cereal.client.domain.model.app.Version
import kotlinx.coroutines.flow.Flow

/**
 * Adapter to the app-update service: fetches the latest published version from remote object
 * storage and downloads its installer. This is a *provider* (an outbound integration), distinct
 * from [com.cereal.client.domain.repository.ApplicationRepository], which owns locally persisted
 * application data (installed/SDK version reads and the version-check timestamp).
 */
interface AppUpdateProvider {
    /**
     * Get the latest available version.
     */
    suspend fun getLatestAvailableAppVersion(): Version

    suspend fun downloadVersion(version: Version): Flow<DownloadStatus>
}

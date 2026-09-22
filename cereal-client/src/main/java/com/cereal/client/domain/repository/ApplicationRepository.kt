package com.cereal.client.domain.repository

import net.swiftzer.semver.SemVer

/**
 * Owns locally available application data: the installed app/SDK versions (build-time reads) and
 * the persisted version-check timestamp. Fetching/downloading remote updates is a provider concern
 * — see [com.cereal.client.domain.provider.AppUpdateProvider].
 */
interface ApplicationRepository {
    /**
     * Returns the current version of the app.
     */
    suspend fun getInstalledVersion(): SemVer

    /**
     * Returns the current SDK version of the app.
     */
    suspend fun getSdkVersion(): SemVer

    suspend fun setVersionCheckTime(timestamp: Long)

    suspend fun getVersionCheckTime(): Long?
}

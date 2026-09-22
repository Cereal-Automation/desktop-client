package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.repository.ApplicationRepository
import net.swiftzer.semver.SemVer

/**
 * In-memory application repository used in the `mock` flavor so version reads and the
 * version-check timestamp work without persistence.
 */
class InMemoryApplicationRepository : ApplicationRepository {
    private val installedVersion = SemVer(1, 0, 0)
    private var versionCheckTime: Long? = null

    override suspend fun getInstalledVersion(): SemVer = installedVersion

    override suspend fun getSdkVersion(): SemVer = SemVer(1, 0, 0)

    override suspend fun setVersionCheckTime(timestamp: Long) {
        versionCheckTime = timestamp
    }

    override suspend fun getVersionCheckTime(): Long? = versionCheckTime
}

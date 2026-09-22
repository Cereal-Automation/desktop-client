package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.repository.ApplicationRepository
import com.cereal.client.infrastructure.data.datasource.database.KeyValueDataSource
import com.cereal.client.infrastructure.data.datasource.preference.ApplicationPreferenceKey
import com.cereal_automation.cereal_client.BuildConfig
import kotlinx.coroutines.flow.first
import net.swiftzer.semver.SemVer

class ApplicationRepositoryImpl(
    private val keyValueDataSource: KeyValueDataSource,
) : ApplicationRepository {
    override suspend fun getInstalledVersion(): SemVer = SemVer.parse(BuildConfig.APP_VERSION)

    override suspend fun getSdkVersion(): SemVer = SemVer.parse(BuildConfig.SDK_VERSION)

    override suspend fun setVersionCheckTime(timestamp: Long) {
        val key = ApplicationPreferenceKey.AppVersionCheckTime.key
        keyValueDataSource.setLongByKey(key, timestamp)
    }

    override suspend fun getVersionCheckTime(): Long? =
        keyValueDataSource
            .getLongByKey(ApplicationPreferenceKey.AppVersionCheckTime.key)
            .first()
}

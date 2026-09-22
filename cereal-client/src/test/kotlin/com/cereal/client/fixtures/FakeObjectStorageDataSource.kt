package com.cereal.client.fixtures

import com.cereal.client.domain.model.OperatingSystemType
import com.cereal.client.infrastructure.data.datasource.network.ObjectStorageDataSource
import com.cereal.client.infrastructure.data.datasource.network.models.LatestAppVersionJsonResponse

/**
 * Configurable in-memory fake of [ObjectStorageDataSource] for repository tests.
 *
 * The version info returned by [getLatestAvailableVersionInfo] can be set globally via
 * [versionInfo], or overridden per-OS via [versionInfoByOs]. The operating system passed in is
 * recorded in [requestedOperatingSystems] so the test can assert which OS was queried.
 */
class FakeObjectStorageDataSource : ObjectStorageDataSource {
    /** Default version info returned when no per-OS override is configured. */
    var versionInfo: LatestAppVersionJsonResponse =
        LatestAppVersionJsonResponse(
            version = "1.0.0",
            minVersion = "1.0.0",
            downloadUrl = "https://downloads.example.com/cereal-client-latest.dmg",
        )

    /** Optional per-OS overrides; takes precedence over [versionInfo] when an entry exists. */
    var versionInfoByOs: Map<OperatingSystemType, LatestAppVersionJsonResponse> = emptyMap()

    /** Operating systems passed to [getLatestAvailableVersionInfo], in call order. */
    val requestedOperatingSystems: MutableList<OperatingSystemType> = mutableListOf()

    override suspend fun getLatestAvailableVersionInfo(operatingSystemType: OperatingSystemType): LatestAppVersionJsonResponse {
        requestedOperatingSystems.add(operatingSystemType)
        return versionInfoByOs[operatingSystemType] ?: versionInfo
    }
}

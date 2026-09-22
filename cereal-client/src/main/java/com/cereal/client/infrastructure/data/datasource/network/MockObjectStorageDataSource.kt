package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.domain.model.OperatingSystemType
import com.cereal.client.infrastructure.data.datasource.network.models.LatestAppVersionJsonResponse

class MockObjectStorageDataSource : ObjectStorageDataSource {
    override suspend fun getLatestAvailableVersionInfo(operatingSystemType: OperatingSystemType): LatestAppVersionJsonResponse {
        val filename =
            when (operatingSystemType) {
                OperatingSystemType.MacOS -> "cereal-client-latest-arm64.dmg"
                OperatingSystemType.Windows -> "cereal-client-latest.exe"
                OperatingSystemType.Linux -> "cereal-client-latest.deb"
            }

        return LatestAppVersionJsonResponse(
            version = "1.9.0",
            minVersion = "1.0.0",
            downloadUrl = "https://downloads.cereal-automation.com/client/$filename",
        )
    }
}

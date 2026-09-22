package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.domain.model.OperatingSystemType
import com.cereal.client.infrastructure.data.datasource.network.models.LatestAppVersionJsonResponse

/**
 * DataSource for interacting with the downloads/object-storage host that serves release metadata.
 * This interface abstracts the underlying network implementation, allowing for
 * different implementations (e.g., Real, Mock).
 */
interface ObjectStorageDataSource {
    /**
     * Retrieves the latest available version information for the specified operating system.
     * @param operatingSystemType The operating system to get version info for.
     * @return A [LatestAppVersionJsonResponse] containing version details.
     * @throws NetworkException if a network error occurs.
     */
    suspend fun getLatestAvailableVersionInfo(operatingSystemType: OperatingSystemType): LatestAppVersionJsonResponse
}

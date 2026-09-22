package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.domain.model.OperatingSystemType
import com.cereal.client.infrastructure.data.datasource.network.models.LatestAppVersionJsonResponse
import io.github.reactivecircus.cache4k.Cache
import kotlin.time.Duration.Companion.hours

class RealObjectStorageDataSource(
    private val dataSource: DownloadsApiClient,
) : ObjectStorageDataSource {
    // Avoid re-fetching the (small) update metadata on every check within a session. An in-memory
    // TTL cache replaces the former OkHttp disk cache: the data is tiny and process-lifetime
    // caching is enough, so it needs no on-disk persistence (and no Windows filesystem workarounds).
    private val cache = Cache.Builder<OperatingSystemType, LatestAppVersionJsonResponse>().expireAfterWrite(1.hours).build()

    override suspend fun getLatestAvailableVersionInfo(operatingSystemType: OperatingSystemType): LatestAppVersionJsonResponse =
        cache.get(operatingSystemType) ?: dataSource.getLatestAvailableVersionInfo(operatingSystemType).also {
            cache.put(operatingSystemType, it)
        }
}

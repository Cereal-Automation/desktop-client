package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.infrastructure.data.datasource.network.MarketplaceDataSource
import com.cereal.sdk.component.license.HttpResponse
import com.cereal.sdk.component.license.LicenseComponent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class LicenseComponentImpl(
    private val dataSource: MarketplaceDataSource,
    private val clock: () -> Long = System::currentTimeMillis,
) : LicenseComponent {
    internal val cache = ConcurrentHashMap<Pair<String, String>, CachedResponse>()
    internal val mutexMap = ConcurrentHashMap<Pair<String, String>, Mutex>()

    /**
     * Checks the license status of the given script using its public ID and a provided salt.
     * This method ensures thread safety and avoids redundant API calls by maintaining a cache
     * and utilizing a lock mechanism for each unique script ID and salt combination.
     *
     * @param publicScriptId The unique public identifier for the script whose license is being checked.
     * @param salt A unique value used to authenticate the license verification request.
     * @return An HttpResponse object representing the result of the license check.
     * @throws IOException If an input or output exception occurs during the license check process.
     */
    @Throws(IOException::class)
    override suspend fun checkScriptLicense(
        publicScriptId: String,
        salt: String,
    ): HttpResponse {
        val key = publicScriptId to salt

        // Check cache before acquiring the lock
        freshCached(key)?.let { return it }

        // Get or create a mutex for this specific key
        val mutex = mutexMap.computeIfAbsent(key) { Mutex() }

        return mutex.withLock {
            try {
                // Double-check cache inside the lock to avoid redundant requests
                freshCached(key) ?: run {
                    val response = dataSource.checkScriptLicense(publicScriptId, salt)
                    val httpResponse = OkHttpResponse(response)
                    // Only successes are shared, and only briefly: a cached failure would block every
                    // later check, and a long-lived success would outlive the server's expires_at window.
                    if (httpResponse.isSuccessful) {
                        httpResponse.body() // Buffer now; the first consumer closes the response.
                        cache[key] = CachedResponse(httpResponse, clock())
                    }
                    httpResponse
                }
            } finally {
                // Cleanup: Remove mutex if no other coroutines are waiting
                mutexMap.remove(key, mutex)
            }
        }
    }

    private fun freshCached(key: Pair<String, String>): HttpResponse? = cache[key]?.takeIf { clock() - it.cachedAt < CACHE_TTL_MS }?.response

    internal data class CachedResponse(
        val response: HttpResponse,
        val cachedAt: Long,
    )

    private companion object {
        // Long enough to collapse a burst of tasks starting together into one request.
        const val CACHE_TTL_MS = 60_000L
    }
}

class OkHttpResponse(
    private val response: Response,
) : HttpResponse {
    private val body: ByteArray by lazy {
        response.body.bytes()
    }

    override val isSuccessful: Boolean
        get() = response.isSuccessful

    override val code: Int
        get() = response.code

    override fun close() {
        response.close()
    }

    override fun header(
        name: String,
        defaultValue: String?,
    ): String? = response.header(name, defaultValue)

    override fun body(): ByteArray = body
}

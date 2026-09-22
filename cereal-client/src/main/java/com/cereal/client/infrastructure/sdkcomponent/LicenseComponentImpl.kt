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
) : LicenseComponent {
    internal val cache = mutableMapOf<Pair<String, String>, HttpResponse>()
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
        cache[key]?.let { return it }

        // Get or create a mutex for this specific key
        val mutex = mutexMap.computeIfAbsent(key) { Mutex() }

        return mutex.withLock {
            try {
                // Double-check cache inside the lock to avoid redundant requests
                cache[key] ?: run {
                    val response = dataSource.checkScriptLicense(publicScriptId, salt)
                    val httpResponse = OkHttpResponse(response)
                    cache[key] = httpResponse
                    httpResponse
                }
            } finally {
                // Cleanup: Remove mutex if no other coroutines are waiting
                mutexMap.remove(key, mutex)
            }
        }
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

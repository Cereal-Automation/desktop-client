package com.cereal.client.infrastructure.data.datasource.network.marsproxies

import com.cereal.client.infrastructure.data.datasource.network.exception.ApiException
import com.cereal.client.infrastructure.data.datasource.network.exception.AuthenticationException
import com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor.JsonHeadersInterceptor
import com.cereal.client.infrastructure.data.datasource.network.marsproxies.dto.MarsProxiesAccountResponse
import com.cereal.client.infrastructure.data.datasource.network.marsproxies.dto.MarsProxiesGenerateProxyListRequest
import com.cereal.client.infrastructure.data.datasource.network.marsproxies.dto.MarsProxiesSubUsersResponse
import com.cereal.client.infrastructure.data.datasource.network.security.CertificatePinnerFactory
import com.cereal.client.infrastructure.data.datasource.network.util.await
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OkHttp + Kotlinx Serialization client for the MarsProxies residential API.
 *
 * Mirrors [com.cereal.client.infrastructure.data.datasource.network.marketplace.MarketplaceApiClient]
 * for structure, minus the signature-verification machinery. The Bearer token is supplied per call
 * because connect-time validation runs against a *candidate* token before it has been stored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarsProxiesApiClient(
    url: String,
    // SPKI pins (`sha256/<base64>`) applied to the API host. An empty list disables pinning
    // (used by tests and the sandboxed mock flavor).
    sslPins: List<String> = emptyList(),
    enableLogging: Boolean = false,
) {
    private val logger = LoggerFactory.getLogger(MarsProxiesApiClient::class.java)
    private val logging = HttpLoggingInterceptor { message -> logger.debug(message) }
    private val apiUrl: String = if (url.endsWith("/")) url else "$url/"

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    private val apiClient: OkHttpClient

    init {
        // BASIC logs only the request/response line — never headers or bodies. The Authorization
        // redaction is belt-and-suspenders in case the level is ever raised for debugging.
        logging.level = if (enableLogging) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        logging.redactHeader("Authorization")

        val builder =
            OkHttpClient
                .Builder()
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(JsonHeadersInterceptor())
                .addInterceptor(logging)

        CertificatePinnerFactory.create(url, sslPins)?.let { builder.certificatePinner(it) }

        apiClient = builder.build()
    }

    /**
     * `GET /v1/residential/me` — validates [token] and returns the account summary.
     *
     * @throws IOException on cancellation, connectivity problems, or timeout.
     * @throws AuthenticationException if the token is rejected (HTTP 401).
     * @throws ApiException on any other non-2xx response or invalid JSON.
     */
    @Throws(IOException::class, ApiException::class)
    suspend fun getAccount(token: String): MarsProxiesAccountResponse {
        val request =
            Request
                .Builder()
                .url("${apiUrl}v1/residential/me")
                .header("Authorization", "Bearer $token")
                .build()

        val response = apiClient.newCall(request).await()
        return handleResponse<MarsProxiesAccountResponse>(response)
    }

    /**
     * `GET /v1/residential/subusers` — lists the sub-users on the account.
     *
     * @throws IOException on cancellation, connectivity problems, or timeout.
     * @throws AuthenticationException if the token is rejected (HTTP 401).
     * @throws ApiException on any other non-2xx response or invalid JSON.
     */
    @Throws(IOException::class, ApiException::class)
    suspend fun getSubUsers(token: String): MarsProxiesSubUsersResponse {
        val request =
            Request
                .Builder()
                .url("${apiUrl}v1/residential/subusers")
                .header("Authorization", "Bearer $token")
                .build()

        val response = apiClient.newCall(request).await()
        return handleResponse<MarsProxiesSubUsersResponse>(response)
    }

    /**
     * `POST /v1/residential/access/generate-proxy-list` — renders a list of connection strings for the
     * supplied [request]. The response is a JSON array of `host:port:user:pass` strings.
     *
     * @throws IOException on cancellation, connectivity problems, or timeout.
     * @throws AuthenticationException if the token is rejected (HTTP 401).
     * @throws ApiException on any other non-2xx response or invalid JSON.
     */
    @Throws(IOException::class, ApiException::class)
    suspend fun generateProxyList(
        token: String,
        request: MarsProxiesGenerateProxyListRequest,
    ): List<String> {
        val requestJson = json.encodeToString(MarsProxiesGenerateProxyListRequest.serializer(), request)
        val httpRequest =
            Request
                .Builder()
                .url("${apiUrl}v1/residential/access/generate-proxy-list")
                .header("Authorization", "Bearer $token")
                .post(requestJson.toRequestBody())
                .build()

        val response = apiClient.newCall(httpRequest).await()
        return handleResponse<List<String>>(response)
    }

    @Throws(ApiException::class)
    private inline fun <reified T> handleResponse(response: Response): T {
        if (response.isSuccessful) {
            response.body.string().let { responseBody ->
                return json.decodeFromString<T>(responseBody)
            }
        }
        if (response.code == HTTP_UNAUTHORIZED) {
            response.body.close()
            throw AuthenticationException()
        }
        response.body.close()
        throw ApiException("MarsProxies request failed with status ${response.code}.")
    }

    private companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 30L
        const val HTTP_UNAUTHORIZED = 401
    }
}

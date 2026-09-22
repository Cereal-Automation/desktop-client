package com.cereal.client.infrastructure.data.datasource.network.marketplace

import com.cereal.client.domain.model.auth.OAuthProvider
import com.cereal.client.infrastructure.data.datasource.network.diagnosticHeaders
import com.cereal.client.infrastructure.data.datasource.network.exception.ApiException
import com.cereal.client.infrastructure.data.datasource.network.exception.AuthenticationException
import com.cereal.client.infrastructure.data.datasource.network.exception.PaidSubscriptionException
import com.cereal.client.infrastructure.data.datasource.network.marketplace.adapter.BigDecimalSerializer
import com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor.AuthorizationInterceptor
import com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor.ClientVersionInterceptor
import com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor.JsonHeadersInterceptor
import com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor.SignatureInterceptor
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.ForgotPasswordRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.GuestLoginRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.LoginRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.OAuthExchangeRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.RegisterRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.CheckScriptLicenseResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.ErrorResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.LoginResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.ScriptDownloadLinkResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.SubscribeScriptResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.SubscribeStatus
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.MarketplaceScript
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.PaginatedResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.Script
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.Subscription
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.User
import com.cereal.client.infrastructure.data.datasource.network.security.CertificatePinnerFactory
import com.cereal.client.infrastructure.data.datasource.network.util.await
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class MarketplaceApiClient(
    url: String,
    version: String,
    publicKey: String = "",
    private val tokenDataSource: AuthorizationInterceptor.TokenDataSource,
    // SPKI pins (`sha256/<base64>`) applied to the API host. Pin the intermediate CA and/or
    // root rather than the leaf so Cloudflare/Google cert rotations don't brick the client;
    // ship a backup pin alongside the active one so the set can be rotated by release. An
    // empty list disables pinning (used by tests and the sandboxed mock flavor).
    sslPins: List<String> = emptyList(),
    enableLogging: Boolean = false,
    verifySignature: Boolean = true,
) {
    private val logger = LoggerFactory.getLogger(MarketplaceApiClient::class.java)
    private val logging = HttpLoggingInterceptor { message -> logger.debug(message) }
    private val apiUrl: String = url + "api/"
    private val secureRandom = SecureRandom()

    private val json =
        Json {
            ignoreUnknownKeys = true
            serializersModule =
                SerializersModule {
                    contextual(BigDecimalSerializer)
                }
        }

    private val downloadClient: OkHttpClient
    private val apiClient: OkHttpClient

    init {
        // BASIC logs only the request/response line (method, URL, status, size) — never headers or
        // bodies — so credentials in auth request bodies and JWTs in responses are not captured.
        // Header redactions are belt-and-suspenders in case the level is ever raised for debugging.
        logging.level = if (enableLogging) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        logging.redactHeader("Authorization")
        logging.redactHeader("Cookie")
        logging.redactHeader("X-Signature")
        logging.redactHeader("X-Salt")

        val builder =
            OkHttpClient
                .Builder()
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(AuthorizationInterceptor(tokenDataSource))
                .addInterceptor(JsonHeadersInterceptor())
                .addInterceptor(ClientVersionInterceptor(version))
                .addInterceptor(logging)

        if (verifySignature) {
            builder.addInterceptor(SignatureInterceptor(publicKey))
        }

        // Pin the API host to the configured SPKI pins (intermediate/root, see CertificatePinnerFactory).
        // Applied to both the API and download clients; a null pinner (empty pins) leaves them unpinned.
        val certificatePinner = CertificatePinnerFactory.create(url, sslPins)
        certificatePinner?.let { builder.certificatePinner(it) }

        apiClient = builder.build()
        downloadClient =
            OkHttpClient
                .Builder()
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DOWNLOAD_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .apply { certificatePinner?.let { certificatePinner(it) } }
                .build()
    }

    /**
     * @throws IOException - if the request could not be executed due to cancellation, a connectivity problem or timeout.
     * Because networks can fail during an exchange, it is possible that the remote server accepted the request before
     * the failure.
     * @throws ApiException - if a non 2xx response was received or if response contains invalid json.
     * @throws AuthenticationException - if the provided credentials are incorrect.
     */
    @Throws(IOException::class, ApiException::class)
    suspend fun authenticate(loginRequestBody: LoginRequestBody): LoginResponse {
        val requestJson: String = json.encodeToString(LoginRequestBody.serializer(), loginRequestBody)

        val request =
            Request
                .Builder()
                .url("${apiUrl}auth/token")
                .post(requestJson.toRequestBody())
                .build()

        val response = apiClient.newCall(request).await()
        return handleResponse<LoginResponse>(response)
    }

    /**
     * @throws IOException - if the request could not be executed due to cancellation, a connectivity problem or timeout.
     * Because networks can fail during an exchange, it is possible that the remote server accepted the request before
     * the failure.
     * @throws ApiException - if a non 2xx response was received or if response contains invalid json.
     * @throws AuthenticationException - if the provided credentials are incorrect.
     */
    @Throws(IOException::class, ApiException::class)
    suspend fun authenticateGuest(guestLoginRequestBody: GuestLoginRequestBody): LoginResponse {
        val requestJson: String = json.encodeToString(GuestLoginRequestBody.serializer(), guestLoginRequestBody)

        val request =
            Request
                .Builder()
                .url("${apiUrl}auth/guest-token")
                .post(requestJson.toRequestBody())
                .build()

        val response = apiClient.newCall(request).await()
        return handleResponse<LoginResponse>(response)
    }

    /**
     * @throws IOException - if the request could not be executed due to cancellation, a connectivity problem or timeout.
     * Because networks can fail during an exchange, it is possible that the remote server accepted the request before
     * the failure.
     * @throws ApiException - if a non 2xx response was received or if response contains invalid json.
     * @throws AuthenticationException - if the provided credentials are incorrect.
     */
    @Throws(IOException::class, ApiException::class)
    suspend fun register(registerRequestBody: RegisterRequestBody): LoginResponse {
        val requestJson: String = json.encodeToString(RegisterRequestBody.serializer(), registerRequestBody)

        val request =
            Request
                .Builder()
                .url("${apiUrl}auth/register")
                .post(requestJson.toRequestBody())
                .build()

        val response = apiClient.newCall(request).await()
        return handleResponse<LoginResponse>(response)
    }

    /**
     * Exchanges the one-time code from a brokered SSO sign-in (via [provider]) for a session token.
     * @throws IOException - if the request could not be executed due to cancellation, a connectivity problem or timeout.
     * @throws ApiException - if a non 2xx response was received or if response contains invalid json.
     * @throws AuthenticationException - if the code is invalid or has expired.
     */
    @Throws(IOException::class, ApiException::class)
    suspend fun exchangeOAuthCode(
        provider: OAuthProvider,
        requestBody: OAuthExchangeRequestBody,
    ): LoginResponse {
        val requestJson: String = json.encodeToString(OAuthExchangeRequestBody.serializer(), requestBody)

        val request =
            Request
                .Builder()
                .url("${apiUrl}auth/${provider.slug}/exchange")
                .post(requestJson.toRequestBody())
                .build()

        val response = apiClient.newCall(request).await()
        return handleResponse<LoginResponse>(response)
    }

    /**
     * Requests a password reset email for the given email address.
     * The server always returns success to prevent user enumeration.
     * @throws IOException - if the request could not be executed due to cancellation, a connectivity problem or timeout.
     * @throws ApiException - if a non 2xx response was received.
     */
    @Throws(IOException::class, ApiException::class)
    suspend fun forgotPassword(email: String) {
        val requestBody = ForgotPasswordRequestBody(email)
        val requestJson: String = json.encodeToString(ForgotPasswordRequestBody.serializer(), requestBody)

        val request =
            Request
                .Builder()
                .url("${apiUrl}auth/forgot-password")
                .post(requestJson.toRequestBody())
                .build()

        val response = apiClient.newCall(request).await()
        if (!response.isSuccessful) {
            val errorResponse =
                response.body.string().let { body ->
                    try {
                        json.decodeFromString<ErrorResponse>(body)
                    } catch (_: Exception) {
                        null
                    }
                }
            throw apiException(response, errorResponse?.message)
        }
    }

    /**
     * Retrieves marketplace scripts with optional search, sorting, filtering, and pagination.
     * @param search Optional search query to filter scripts by title or tag.
     * @param sort The field to sort by. Allowed values: "title", "price", "created_at", "updated_at", "rating".
     * @param direction The sort direction. Allowed values: "asc", "desc".
     * @param community Optional filter for community scripts.
     * @param isFree Optional filter for free or paid scripts.
     * @param page The page number to retrieve (1-based).
     * @param perPage The number of scripts per page.
     * @throws IOException - if the request could not be executed due to cancellation, a connectivity problem or timeout.
     * @throws ApiException - if a non 2xx response was received or if response contains invalid json.
     */
    @Throws(IOException::class, ApiException::class)
    suspend fun getMarketplaceScripts(
        search: String? = null,
        sort: String = "title",
        direction: String = "asc",
        community: Boolean? = null,
        isFree: Boolean? = null,
        page: Int = 1,
        perPage: Int = 20,
    ): PaginatedResponse<MarketplaceScript> {
        val urlBuilder =
            "${apiUrl}marketplace/scripts"
                .toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("sort", sort)
                ?.addQueryParameter("direction", direction)
                ?.addQueryParameter("page", page.toString())
                ?.addQueryParameter("per_page", perPage.toString())

        search?.let { urlBuilder?.addQueryParameter("search", it) }
        community?.let { urlBuilder?.addQueryParameter("community", it.toString()) }
        isFree?.let { urlBuilder?.addQueryParameter("is_free", it.toString()) }

        val url = urlBuilder?.build() ?: throw IOException("Not a valid url")

        val request =
            Request
                .Builder()
                .url(url)
                .build()

        val response = apiClient.newCall(request).await()
        return handleResponse<PaginatedResponse<MarketplaceScript>>(response)
    }

    /**
     * Subscribes the authenticated user to a marketplace script.
     * A 409 response (already subscribed) is treated as success.
     * @throws IOException - if the request could not be executed due to cancellation, a connectivity problem or timeout.
     * @throws ApiException - if a non 2xx/409 response was received.
     * @throws AuthenticationException - if the user is not authenticated.
     */
    @Throws(IOException::class, ApiException::class)
    suspend fun subscribeToScript(publicScriptId: String): SubscribeScriptResponse {
        requireToken()
        val request =
            Request
                .Builder()
                .url("${apiUrl}marketplace/scripts/$publicScriptId/subscribe")
                .post("".toRequestBody())
                .build()

        val response = apiClient.newCall(request).await()
        // 409 means already subscribed — treat it as a successful no-op
        if (response.code == HTTP_CONFLICT) {
            return SubscribeScriptResponse(status = SubscribeStatus.ALREADY_SUBSCRIBED)
        }
        return handleResponse<SubscribeScriptResponse>(response)
    }

    /**
     * Unsubscribes the authenticated user from a marketplace script.
     * A 404 response with status "not_subscribed" is treated as success — the operation is idempotent.
     * A 403 response with status "paid_subscription" indicates a Stripe-managed subscription that must be cancelled via the marketplace website.
     * @throws IOException - if the request could not be executed due to cancellation, a connectivity problem or timeout.
     * @throws ApiException - if an unexpected non-2xx response was received.
     * @throws AuthenticationException - if the user is not authenticated.
     * @throws PaidSubscriptionException - if the script has an active paid subscription that must be cancelled via the marketplace website.
     */
    @Throws(IOException::class, ApiException::class)
    suspend fun unsubscribeFromScript(publicScriptId: String) {
        requireToken()
        val request =
            Request
                .Builder()
                .url("${apiUrl}marketplace/scripts/$publicScriptId/subscribe")
                .delete()
                .build()

        val response = apiClient.newCall(request).await()
        if (response.isSuccessful) {
            response.body.close()
            return
        }
        if (response.code == HTTP_UNAUTHORIZED) {
            response.body.close()
            throw AuthenticationException()
        }
        val errorResponse =
            response.body.string().let { body ->
                try {
                    json.decodeFromString<ErrorResponse>(body)
                } catch (_: Exception) {
                    null
                }
            }
        when {
            response.code == HTTP_NOT_FOUND && errorResponse?.status == "not_subscribed" -> return
            response.code == HTTP_FORBIDDEN && errorResponse?.status == "paid_subscription" -> throw PaidSubscriptionException()
            else -> throw apiException(response, errorResponse?.message, errorResponse?.errors)
        }
    }

    /**
     * @throws IOException - if the request could not be executed due to cancellation, a connectivity problem or timeout.
     * Because networks can fail during an exchange, it is possible that the remote server accepted the request before
     * the failure.
     * @throws ApiException - if a non 2xx response was received or if response contains invalid json.
     * @throws AuthenticationException - if the user is not authenticated.
     */
    @Throws(IOException::class, ApiException::class)
    suspend fun getMySubscriptions(): List<Subscription> {
        requireToken()
        val request =
            Request
                .Builder()
                .url("${apiUrl}subscription/me")
                .build()

        val response = apiClient.newCall(request).await()
        return handleResponse<List<Subscription>>(response)
    }

    /**
     * @throws IOException - if the request could not be executed due to cancellation, a connectivity problem or timeout.
     * Because networks can fail during an exchange, it is possible that the remote server accepted the request before
     * the failure.
     * @throws ApiException - if a non 2xx response was received or if response contains invalid json.
     * @throws AuthenticationException - if the user is not authenticated.
     */
    @Throws(IOException::class, ApiException::class)
    suspend fun getMyTeamScripts(): List<Script> {
        requireToken()
        val request =
            Request
                .Builder()
                .url("${apiUrl}script/myteam")
                .build()

        val response = apiClient.newCall(request).await()
        return handleResponse<List<Script>>(response)
    }

    /**
     * @throws IOException - if the request could not be executed due to cancellation, a connectivity problem or timeout.
     * Because networks can fail during an exchange, it is possible that the remote server accepted the request before
     * the failure.
     * @throws ApiException - if a non 2xx response was received or if response contains invalid json.
     * @throws AuthenticationException - if the user is not authenticated.
     */
    @Throws(IOException::class, ApiException::class)
    suspend fun getScriptDownloadLink(
        publicScriptId: String,
        versionCode: Long,
    ): ScriptDownloadLinkResponse {
        requireToken()

        val url =
            "${apiUrl}script/$publicScriptId/download"
                .toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("version_code", versionCode.toString())
                ?.build()

        return url?.let {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .build()

            val response = apiClient.newCall(request).await()
            handleResponse<ScriptDownloadLinkResponse>(response)
        } ?: throw IOException("Not a valid url: $url")
    }

    /**
     * @throws IOException - if the request could not be executed due to cancellation, a connectivity problem or timeout.
     * Because networks can fail during an exchange, it is possible that the remote server accepted the request before
     * the failure.
     * @throws ApiException - if a non 2xx response was received or if response contains invalid json.
     * @throws AuthenticationException - if the user is not authenticated.
     */
    @Throws(IOException::class, ApiException::class)
    suspend fun getFreeScripts(): List<Script> {
        requireToken()
        val request =
            Request
                .Builder()
                .url("${apiUrl}script/free")
                .build()

        val response = apiClient.newCall(request).await()
        return handleResponse<List<Script>>(response)
    }

    /**
     * @throws IOException - if the request could not be executed due to cancellation, a connectivity problem or timeout.
     * Because networks can fail during an exchange, it is possible that the remote server accepted the request before
     * the failure.
     * @throws ApiException - if a non 2xx response was received or if response contains invalid json.
     * @throws AuthenticationException - if the user is not authenticated.
     */
    @Throws(IOException::class, ApiException::class)
    suspend fun verifyLicense(publicScriptId: String): CheckScriptLicenseResponse {
        requireToken()
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val salt = (1..SALT_LENGTH).map { allowedChars[secureRandom.nextInt(allowedChars.size)] }.joinToString("")
        val request =
            Request
                .Builder()
                .url("${apiUrl}script/$publicScriptId/verify_license")
                // Use a per-call cryptographically secure random salt. The returned signature is not verified here
                // because we don't know the script's public key, but using a fresh salt avoids a stale literal.
                .header("X-Script-Salt", salt)
                .build()

        val response = apiClient.newCall(request).await()
        return handleResponse<CheckScriptLicenseResponse>(response)
    }

    @Throws(IOException::class)
    suspend fun checkScriptLicense(
        publicScriptId: String,
        salt: String,
    ): Response {
        requireToken()

        val request =
            Request
                .Builder()
                .url("${apiUrl}script/$publicScriptId/verify_license")
                .header("X-Script-Salt", salt)
                .build()

        return apiClient.newCall(request).await()
    }

    suspend fun downloadScript(
        publicScriptId: String,
        versionCode: Long,
    ): InputStream {
        val downloadLink = getScriptDownloadLink(publicScriptId, versionCode).downloadLink

        val request =
            Request
                .Builder()
                .url(downloadLink)
                .build()

        val response = downloadClient.newCall(request).await()

        if (!response.isSuccessful) {
            throw apiException(response, "Failed to download script.")
        }

        return response.body.byteStream()
    }

    suspend fun getAuthenticatedUser(): User = getUser()

    private fun requireToken() {
        if (tokenDataSource.getToken() == null) {
            throw AuthenticationException()
        }
    }

    private suspend fun getUser(): User {
        val request =
            Request
                .Builder()
                .url("${apiUrl}me")
                .build()

        val response = apiClient.newCall(request).await()
        return handleResponse<User>(response)
    }

    @Throws(ApiException::class)
    private suspend inline fun <reified T> handleResponse(response: Response): T {
        if (response.isSuccessful) {
            response.body.string().let { responseBody ->
                return json.decodeFromString<T>(responseBody)
            }
        } else {
            if (response.code == HTTP_UNAUTHORIZED) {
                throw AuthenticationException()
            }

            val errorResponse =
                response.body.string().let { responseBody ->
                    try {
                        json.decodeFromString<ErrorResponse>(responseBody)
                    } catch (_: Exception) {
                        null
                    }
                }

            throw apiException(response, errorResponse?.message, errorResponse?.errors)
        }
    }

    /**
     * Builds an [ApiException] carrying the status and CDN diagnostics of the failed response.
     *
     * A null [message] means the body was absent or not the JSON error shape we expect — typically an
     * edge-generated HTML page that never reached the backend. Falling back to the status line keeps
     * a 429 distinguishable from a 502 in reports, where the old "Unknown error." was a dead end.
     */
    private fun apiException(
        response: Response,
        message: String?,
        validationErrors: List<String>? = null,
    ) = ApiException(
        message = message ?: "Request failed with HTTP ${response.code}.",
        validationErrors = validationErrors,
        httpStatus = response.code,
        url = response.request.url.toString(),
        edgeHeaders = response.diagnosticHeaders(),
    )

    private companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 30L
        const val DOWNLOAD_READ_TIMEOUT_SECONDS = 60L
        const val SALT_LENGTH = 32

        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
    }
}

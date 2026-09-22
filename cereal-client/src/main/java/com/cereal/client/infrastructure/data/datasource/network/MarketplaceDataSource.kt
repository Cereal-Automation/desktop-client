package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.domain.model.auth.OAuthProvider
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.GuestLoginRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.LoginRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.OAuthExchangeRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.RegisterRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.CheckScriptLicenseResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.LoginResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.ScriptDownloadLinkResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.SubscribeScriptResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.MarketplaceScript
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.PaginatedResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.Script
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.Subscription
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.User
import okhttp3.Response
import java.io.InputStream

/**
 * DataSource for interacting with the Cereal Marketplace API.
 * This interface abstracts the underlying network implementation, allowing for
 * different implementations (e.g., Real, Mock).
 */
interface MarketplaceDataSource {
    /**
     * Authenticates the user with the marketplace.
     * @param loginRequestBody The login credentials.
     * @throws IOException if a network error occurs.
     * @throws ApiException if the API returns an error.
     * @throws AuthenticationException if authentication fails.
     */
    suspend fun authenticate(loginRequestBody: LoginRequestBody): LoginResponse

    /**
     * Authenticates as a guest with the marketplace.
     * @param guestLoginRequestBody The guest login credentials.
     * @throws IOException if a network error occurs.
     * @throws ApiException if the API returns an error.
     */
    suspend fun authenticateGuest(guestLoginRequestBody: GuestLoginRequestBody): LoginResponse

    /**
     * Exchanges the one-time code from a brokered SSO sign-in (via [provider]) for a session token.
     * @throws IOException if a network error occurs.
     * @throws ApiException if the API returns an error.
     * @throws AuthenticationException if the code is invalid or has expired.
     */
    suspend fun exchangeOAuthCode(
        provider: OAuthProvider,
        oauthExchangeRequestBody: OAuthExchangeRequestBody,
    ): LoginResponse

    /**
     * Registers a new user with the marketplace.
     * @param registerRequestBody The registration details.
     * @throws IOException if a network error occurs.
     * @throws ApiException if the API returns an error.
     * @throws AuthenticationException if authentication fails.
     */
    suspend fun register(registerRequestBody: RegisterRequestBody): LoginResponse

    /**
     * Retrieves the list of subscriptions for the authenticated user.
     * @return A list of [Subscription]s.
     * @throws IOException if a network error occurs.
     * @throws ApiException if the API returns an error.
     * @throws AuthenticationException if the user is not authenticated.
     */
    suspend fun getMySubscriptions(): List<Subscription>

    /**
     * Retrieves the list of scripts available to the user's team.
     * @return A list of [Script]s.
     * @throws IOException if a network error occurs.
     * @throws ApiException if the API returns an error.
     * @throws AuthenticationException if the user is not authenticated.
     */
    suspend fun getMyTeamScripts(): List<Script>

    /**
     * Retrieves a download link for a specific script version.
     * @param publicScriptId The public identifier of the script.
     * @param versionCode The version code of the script to download.
     * @return A [ScriptDownloadLinkResponse] containing the download URL.
     * @throws IOException if a network error occurs.
     * @throws ApiException if the API returns an error.
     * @throws AuthenticationException if the user is not authenticated.
     */
    suspend fun getScriptDownloadLink(
        publicScriptId: String,
        versionCode: Long,
    ): ScriptDownloadLinkResponse

    /**
     * Retrieves a list of free scripts available on the marketplace.
     * @return A list of free [Script]s.
     * @throws IOException if a network error occurs.
     * @throws ApiException if the API returns an error.
     * @throws AuthenticationException if the user is not authenticated.
     */
    suspend fun getFreeScripts(): List<Script>

    /**
     * Verifies the license for a specific script.
     * @param publicScriptId The public identifier of the script.
     * @return A [CheckScriptLicenseResponse] indicating if the user has a valid license.
     * @throws IOException if a network error occurs.
     * @throws ApiException if the API returns an error.
     * @throws AuthenticationException if the user is not authenticated.
     */
    suspend fun verifyLicense(publicScriptId: String): CheckScriptLicenseResponse

    /**
     * Checks the license for a specific script using a provided salt.
     * This is a lower-level method returning the raw Response.
     * @param publicScriptId The public identifier of the script.
     * @param salt A salt string used for verification.
     * @return The raw [Response] from the API.
     * @throws IOException if a network error occurs.
     */
    suspend fun checkScriptLicense(
        publicScriptId: String,
        salt: String,
    ): Response

    /**
     * Downloads the script file.
     * @param publicScriptId The public identifier of the script.
     * @param versionCode The version code of the script to download.
     * @return An [InputStream] of the script file content.
     * @throws IOException if a network error occurs.
     * @throws ApiException if the API returns an error.
     */
    suspend fun downloadScript(
        publicScriptId: String,
        versionCode: Long,
    ): InputStream

    /**
     * Retrieves marketplace scripts with optional search, sorting, filtering, and pagination.
     * @param search Optional search query to filter scripts by title or tag.
     * @param sort The field to sort by.
     * @param direction The sort direction.
     * @param isFree Optional filter for free or paid scripts.
     * @param page The page number to retrieve (1-based).
     * @param perPage The number of scripts per page.
     * @return A [PaginatedResponse] of [MarketplaceScript]s.
     * @throws IOException if a network error occurs.
     * @throws ApiException if the API returns an error.
     */
    suspend fun getMarketplaceScripts(
        search: String? = null,
        sort: String = "title",
        direction: String = "asc",
        community: Boolean? = null,
        isFree: Boolean? = null,
        page: Int = 1,
        perPage: Int = 20,
    ): PaginatedResponse<MarketplaceScript>

    /**
     * Subscribes the authenticated user to a marketplace script.
     * @param publicScriptId The public identifier of the script.
     * @return The [SubscribeScriptResponse] from the API.
     * @throws IOException if a network error occurs.
     * @throws ApiException if the API returns an error.
     * @throws AuthenticationException if the user is not authenticated.
     */
    suspend fun subscribeToScript(publicScriptId: String): SubscribeScriptResponse

    /**
     * Unsubscribes the authenticated user from a marketplace script.
     * Idempotent — does not throw if there is no active subscription.
     * @param publicScriptId The public identifier of the script.
     * @throws IOException if a network error occurs.
     * @throws ApiException if the API returns an error.
     * @throws AuthenticationException if the user is not authenticated.
     */
    suspend fun unsubscribeFromScript(publicScriptId: String)

    /**
     * Retrieves the currently authenticated user's details.
     * @return The [User] object.
     * @throws IOException if a network error occurs.
     * @throws ApiException if the API returns an error.
     * @throws AuthenticationException if the user is not authenticated.
     */
    suspend fun getAuthenticatedUser(): User

    /**
     * Sends a password reset email to the given address.
     * @throws IOException if a network error occurs.
     * @throws ApiException if the API returns an error.
     */
    suspend fun forgotPassword(email: String)
}

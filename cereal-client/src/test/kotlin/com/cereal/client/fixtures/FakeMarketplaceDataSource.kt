package com.cereal.client.fixtures

import com.cereal.client.domain.model.auth.OAuthProvider
import com.cereal.client.infrastructure.data.datasource.network.MarketplaceDataSource
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.GuestLoginRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.LoginRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.OAuthExchangeRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.RegisterRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.CheckScriptLicenseResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.LoginResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.ScriptDownloadLinkResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.SubscribeScriptResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.SubscribeStatus
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.MarketplaceScript
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.PaginatedResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.Script
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.Subscription
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.User
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Configurable in-memory fake of [MarketplaceDataSource] for repository tests.
 *
 * Query-style methods return canned values exposed as public mutable `var`s the test can set;
 * mutating calls (subscribe/unsubscribe) record their inputs so the test can assert against them.
 */
class FakeMarketplaceDataSource : MarketplaceDataSource {
    /** Canned response for [authenticate]/[authenticateGuest]/[register]. */
    var loginResponse: LoginResponse =
        LoginResponse(
            token = "fake-token",
            user = defaultUser,
        )

    /** Canned user returned by [getAuthenticatedUser]. */
    var authenticatedUser: User = defaultUser

    /** Canned subscriptions returned by [getMySubscriptions]. */
    var mySubscriptions: List<Subscription> = emptyList()

    /** Canned scripts returned by [getMyTeamScripts]. */
    var myTeamScripts: List<Script> = emptyList()

    /** Canned scripts returned by [getFreeScripts]. */
    var freeScripts: List<Script> = emptyList()

    /** Canned response for [getScriptDownloadLink]. */
    var scriptDownloadLinkResponse: ScriptDownloadLinkResponse =
        ScriptDownloadLinkResponse(downloadLink = "https://downloads.example.com/script.jar")

    /** Canned response for [verifyLicense]. */
    var verifyLicenseResponse: CheckScriptLicenseResponse = CheckScriptLicenseResponse(isLicensed = true)

    /** Canned paginated marketplace scripts returned by [getMarketplaceScripts]. */
    var marketplaceScripts: PaginatedResponse<MarketplaceScript> =
        PaginatedResponse(
            data = emptyList(),
            currentPage = 1,
            lastPage = 1,
            total = 0,
            perPage = 20,
        )

    /** Canned response for [subscribeToScript]. */
    var subscribeScriptResponse: SubscribeScriptResponse = SubscribeScriptResponse(status = SubscribeStatus.SUBSCRIBED)

    /** Bytes streamed back from [downloadScript]. */
    var scriptBytes: ByteArray = ByteArray(0)

    /** Raw [Response] returned by [checkScriptLicense]; must be configured by the test before use. */
    var checkScriptLicenseResponse: Response? = null

    /** Public identifiers passed to [subscribeToScript], in call order. */
    val subscribedPackageIds: MutableList<String> = mutableListOf()

    /** Public identifiers passed to [unsubscribeFromScript], in call order. */
    val unsubscribedPackageIds: MutableList<String> = mutableListOf()

    /** Emails passed to [forgotPassword], in call order. */
    val forgotPasswordEmails: MutableList<String> = mutableListOf()

    override suspend fun authenticate(loginRequestBody: LoginRequestBody): LoginResponse = loginResponse

    override suspend fun authenticateGuest(guestLoginRequestBody: GuestLoginRequestBody): LoginResponse = loginResponse

    override suspend fun exchangeOAuthCode(
        provider: OAuthProvider,
        oauthExchangeRequestBody: OAuthExchangeRequestBody,
    ): LoginResponse = loginResponse

    override suspend fun register(registerRequestBody: RegisterRequestBody): LoginResponse = loginResponse

    override suspend fun getMySubscriptions(): List<Subscription> = mySubscriptions

    override suspend fun getMyTeamScripts(): List<Script> = myTeamScripts

    override suspend fun getScriptDownloadLink(
        publicScriptId: String,
        versionCode: Long,
    ): ScriptDownloadLinkResponse = scriptDownloadLinkResponse

    override suspend fun getFreeScripts(): List<Script> = freeScripts

    override suspend fun verifyLicense(publicScriptId: String): CheckScriptLicenseResponse = verifyLicenseResponse

    override suspend fun checkScriptLicense(
        publicScriptId: String,
        salt: String,
    ): Response = checkScriptLicenseResponse ?: error("checkScriptLicenseResponse not configured on FakeMarketplaceDataSource")

    override suspend fun downloadScript(
        publicScriptId: String,
        versionCode: Long,
    ): InputStream = ByteArrayInputStream(scriptBytes)

    override suspend fun getMarketplaceScripts(
        search: String?,
        sort: String,
        direction: String,
        community: Boolean?,
        isFree: Boolean?,
        page: Int,
        perPage: Int,
    ): PaginatedResponse<MarketplaceScript> = marketplaceScripts

    override suspend fun subscribeToScript(publicScriptId: String): SubscribeScriptResponse {
        subscribedPackageIds.add(publicScriptId)
        return subscribeScriptResponse
    }

    override suspend fun unsubscribeFromScript(publicScriptId: String) {
        unsubscribedPackageIds.add(publicScriptId)
    }

    override suspend fun getAuthenticatedUser(): User = authenticatedUser

    override suspend fun forgotPassword(email: String) {
        forgotPasswordEmails.add(email)
    }

    private companion object {
        private val defaultUser =
            User(
                id = "user-1",
                name = "Test User",
                email = "test@example.com",
                key = "encryption-key",
                isGuest = false,
            )
    }
}

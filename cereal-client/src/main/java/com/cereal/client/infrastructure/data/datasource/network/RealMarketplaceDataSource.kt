package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.domain.model.auth.OAuthProvider
import com.cereal.client.infrastructure.data.datasource.network.marketplace.MarketplaceApiClient
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

class RealMarketplaceDataSource(
    private val apiClient: MarketplaceApiClient,
) : MarketplaceDataSource {
    override suspend fun authenticate(loginRequestBody: LoginRequestBody): LoginResponse = apiClient.authenticate(loginRequestBody)

    override suspend fun authenticateGuest(guestLoginRequestBody: GuestLoginRequestBody): LoginResponse = apiClient.authenticateGuest(guestLoginRequestBody)

    override suspend fun exchangeOAuthCode(
        provider: OAuthProvider,
        oauthExchangeRequestBody: OAuthExchangeRequestBody,
    ): LoginResponse = apiClient.exchangeOAuthCode(provider, oauthExchangeRequestBody)

    override suspend fun register(registerRequestBody: RegisterRequestBody): LoginResponse = apiClient.register(registerRequestBody)

    override suspend fun getMySubscriptions(): List<Subscription> = apiClient.getMySubscriptions()

    override suspend fun getMyTeamScripts(): List<Script> = apiClient.getMyTeamScripts()

    override suspend fun getScriptDownloadLink(
        publicScriptId: String,
        versionCode: Long,
    ): ScriptDownloadLinkResponse = apiClient.getScriptDownloadLink(publicScriptId, versionCode)

    override suspend fun getFreeScripts(): List<Script> = apiClient.getFreeScripts()

    override suspend fun verifyLicense(publicScriptId: String): CheckScriptLicenseResponse = apiClient.verifyLicense(publicScriptId)

    override suspend fun checkScriptLicense(
        publicScriptId: String,
        salt: String,
    ): Response = apiClient.checkScriptLicense(publicScriptId, salt)

    override suspend fun downloadScript(
        publicScriptId: String,
        versionCode: Long,
    ): InputStream = apiClient.downloadScript(publicScriptId, versionCode)

    override suspend fun getAuthenticatedUser(): User = apiClient.getAuthenticatedUser()

    override suspend fun subscribeToScript(publicScriptId: String): SubscribeScriptResponse = apiClient.subscribeToScript(publicScriptId)

    override suspend fun unsubscribeFromScript(publicScriptId: String) = apiClient.unsubscribeFromScript(publicScriptId)

    override suspend fun getMarketplaceScripts(
        search: String?,
        sort: String,
        direction: String,
        community: Boolean?,
        isFree: Boolean?,
        page: Int,
        perPage: Int,
    ): PaginatedResponse<MarketplaceScript> = apiClient.getMarketplaceScripts(search, sort, direction, community, isFree, page, perPage)

    override suspend fun forgotPassword(email: String) = apiClient.forgotPassword(email)
}

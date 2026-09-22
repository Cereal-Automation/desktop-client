package com.cereal.client.infrastructure.provider

import com.cereal.client.application.exception.InvalidLoginCredentialsException
import com.cereal.client.application.exception.LoginValidationException
import com.cereal.client.application.exception.OAuthAuthenticationException
import com.cereal.client.application.exception.RegistrationValidationException
import com.cereal.client.domain.model.auth.OAuthProvider
import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.domain.model.user.User
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.network.MarketplaceDataSource
import com.cereal.client.infrastructure.data.datasource.network.OAuthDataSource
import com.cereal.client.infrastructure.data.datasource.network.SubscriptionDataSource
import com.cereal.client.infrastructure.data.datasource.network.exception.ApiException
import com.cereal.client.infrastructure.data.datasource.network.exception.AuthenticationException
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.GuestLoginRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.LoginRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.OAuthExchangeRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.request.RegisterRequestBody
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.Script
import io.github.reactivecircus.cache4k.Cache
import java.net.InetAddress
import kotlin.time.Duration.Companion.hours
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.Release as ApiRelease

class AuthProviderImpl(
    private val marketplaceDataSource: MarketplaceDataSource,
    private val userSession: UserSession,
    private val subscriptionDataSource: SubscriptionDataSource,
    private val oauthDataSource: OAuthDataSource,
) : AuthProvider {
    private val myTeamScriptsCache = Cache.Builder<String, List<ScriptEntitlement>>().expireAfterWrite(1.hours).build()

    override suspend fun authenticate(
        email: String,
        password: String,
    ): User {
        try {
            val loginResponse = marketplaceDataSource.authenticate(LoginRequestBody(email, password, systemName()))
            return loginResponse.user.toDomain(loginResponse.token)
        } catch (_: AuthenticationException) {
            throw InvalidLoginCredentialsException()
        } catch (e: ApiException) {
            // A 422 with field errors (e.g. "The email field is required.") is an expected validation
            // failure, not a defect: surface it as a domain CerealException so it reaches the user with
            // the real message instead of being reported to Sentry as an unexpected error. Mirrors the
            // register() path below.
            e.validationErrors?.let { errors ->
                if (errors.isNotEmpty()) {
                    throw LoginValidationException(errors.joinToString("\n"))
                }
            }
            throw e
        }
    }

    override suspend fun authenticateGuest(): User {
        val loginResponse = marketplaceDataSource.authenticateGuest(GuestLoginRequestBody(systemName()))
        return loginResponse.user.toDomain(loginResponse.token)
    }

    override suspend fun authenticateWith(provider: OAuthProvider): User {
        val code = oauthDataSource.obtainOneTimeCode(provider)
        try {
            val loginResponse = marketplaceDataSource.exchangeOAuthCode(provider, OAuthExchangeRequestBody(code, systemName()))
            return loginResponse.user.toDomain(loginResponse.token)
        } catch (_: AuthenticationException) {
            // An invalid/expired one-time code — surface as a domain error like the password path does.
            throw OAuthAuthenticationException("Sign-in could not be completed. Please try again.")
        }
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
    ): User {
        try {
            val loginResponse = marketplaceDataSource.register(RegisterRequestBody(name, email, password, systemName()))
            return loginResponse.user.toDomain(loginResponse.token)
        } catch (e: ApiException) {
            e.validationErrors?.let { errors ->
                if (errors.isNotEmpty()) {
                    throw RegistrationValidationException(errors.joinToString("\n"))
                }
            }
            throw e
        }
    }

    override suspend fun getSubscriptions(ignoreCache: Boolean): List<Subscription> {
        if (ignoreCache) subscriptionDataSource.invalidateCache()
        return subscriptionDataSource.getSubscriptions()
    }

    override suspend fun invalidateSubscriptionsCache() {
        subscriptionDataSource.invalidateCache()
    }

    override suspend fun getMyTeamScripts(ignoreCache: Boolean): List<ScriptEntitlement> =
        userSession.requireUser().let {
            val cachedMyTeamsScripts = if (!ignoreCache) myTeamScriptsCache.get(it.id) else null

            cachedMyTeamsScripts ?: run {
                val myTeamScripts =
                    marketplaceDataSource
                        .getMyTeamScripts()
                        .map { script -> script.toDomain() }
                myTeamScriptsCache.put(it.id, myTeamScripts)
                myTeamScripts
            }
        }

    override suspend fun forgotPassword(email: String) {
        marketplaceDataSource.forgotPassword(email)
    }

    private fun Script.toDomain(): ScriptEntitlement =
        ScriptEntitlement(
            this.publicIdentifier,
            this.title,
            this.latestRelease?.toDomain(),
            this.latestDraftRelease?.toDomain(),
            this.shortDescription,
            this.price,
            capacity = ScriptCapacity.of(this.capacity, this.capacityUnit),
        )

    private fun ApiRelease.toDomain(): Release = Release(versionName, versionCode, releaseNotes)

    private fun systemName(): String =
        try {
            InetAddress.getLocalHost().hostName
        } catch (_: Exception) {
            "Unknown"
        }

    private fun com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.User.toDomain(
        accessToken: String,
    ): User = User(this.id, this.name, this.email, this.key, accessToken, this.isGuest)
}

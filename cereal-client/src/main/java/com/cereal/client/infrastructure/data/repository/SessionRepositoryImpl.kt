package com.cereal.client.infrastructure.data.repository

import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.domain.model.user.User
import com.cereal.client.domain.repository.SessionRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.auth.UserTokenDataSource
import com.cereal.client.infrastructure.data.datasource.network.MarketplaceDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory

class SessionRepositoryImpl(
    private val marketplaceDataSource: MarketplaceDataSource,
    private val userSession: UserSession,
    private val userTokenDataSource: UserTokenDataSource,
) : SessionRepository {
    private val logger = LoggerFactory.getLogger(SessionRepositoryImpl::class.java)

    override suspend fun setSessionUser(user: User?) {
        user?.let {
            userTokenDataSource.saveToken(it.accessToken)
        } ?: run {
            userTokenDataSource.saveToken(null)
        }

        userSession.setUser(user)
    }

    override suspend fun getStoredUser(): User? {
        val token = userTokenDataSource.loadToken()

        return if (token != null) {
            // If we have a token, set it in the data source so we can make authenticated requests
            try {
                // Try to get the user from the API
                marketplaceDataSource.getAuthenticatedUser().toDomain(token)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                // A network/auth failure here is expected (offline, expired token) and CrashReporter
                // filters that noise; anything else is an unexpected defect that would otherwise be
                // silently masked as "no stored user", so surface it to Sentry.
                logger.warn("Failed to restore stored user from token", e)
                CrashReporter.report(e)
                null
            }
        } else {
            null
        }
    }

    override suspend fun getAuthenticatedUserFlow(): Flow<User?> = userSession.getUserFlow()

    private fun com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.User.toDomain(
        accessToken: String,
    ): User = User(this.id, this.name, this.email, this.key, accessToken, this.isGuest)
}

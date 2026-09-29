package com.cereal.client.infrastructure.data.repository

import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.application.exception.MarketplaceUnreachableException
import com.cereal.client.domain.model.user.User
import com.cereal.client.domain.repository.SessionRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.auth.UserTokenDataSource
import com.cereal.client.infrastructure.data.datasource.network.MarketplaceDataSource
import com.cereal.client.infrastructure.data.datasource.network.exception.ApiException
import com.cereal.client.infrastructure.data.datasource.network.exception.AuthenticationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory
import java.io.IOException

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
        val token = userTokenDataSource.loadToken() ?: return null

        return try {
            marketplaceDataSource.getAuthenticatedUser().toDomain(token)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: AuthenticationException) {
            // A 401: the marketplace rejected the stored token, so the session is gone.
            logger.info("Stored session was rejected by the marketplace", e)
            null
        } catch (e: IOException) {
            throw MarketplaceUnreachableException(e)
        } catch (e: ApiException) {
            if ((e.httpStatus ?: 0) >= HTTP_SERVER_ERROR) throw MarketplaceUnreachableException(e)
            unexpected(e)
        } catch (e: Exception) {
            unexpected(e)
        }
    }

    /** An unexpected defect that would otherwise be silently masked as "no stored user", so surface it to Sentry. */
    private fun unexpected(e: Exception): User? {
        logger.warn("Failed to restore stored user from token", e)
        CrashReporter.report(e)
        return null
    }

    override suspend fun getAuthenticatedUserFlow(): Flow<User?> = userSession.getUserFlow()

    private fun com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.User.toDomain(
        accessToken: String,
    ): User = User(this.id, this.name, this.email, this.key, accessToken, this.isGuest)

    private companion object {
        const val HTTP_SERVER_ERROR = 500
    }
}

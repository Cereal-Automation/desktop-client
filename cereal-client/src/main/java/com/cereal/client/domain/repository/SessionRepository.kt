package com.cereal.client.domain.repository

import com.cereal.client.domain.model.user.User
import kotlinx.coroutines.flow.Flow

/**
 * Owns the local authentication session: persisting the signed-in user's token, restoring the
 * stored user on startup, and exposing the current session as a flow. Talking to the marketplace
 * to actually authenticate, register or fetch entitlements is a provider concern — see
 * [com.cereal.client.domain.provider.AuthProvider].
 */
interface SessionRepository {
    suspend fun setSessionUser(user: User?)

    /**
     * Returns the stored user once the marketplace accepts its token, or null when there is no
     * stored token or the marketplace rejected it (a `401`, session lost).
     *
     * Throws `MarketplaceUnreachableException` (application layer) on a network failure or 5xx: the
     * stored session may still be valid, so callers retry or fall back instead of showing login.
     */
    suspend fun getStoredUser(): User?

    /**
     * Returns the user if the user is authenticated or else null.
     */
    suspend fun getAuthenticatedUserFlow(): Flow<User?>
}

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

    suspend fun getStoredUser(): User?

    /**
     * Returns the user if the user is authenticated or else null.
     */
    suspend fun getAuthenticatedUserFlow(): Flow<User?>
}

package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.user.User
import com.cereal.client.domain.repository.SessionRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import kotlinx.coroutines.flow.Flow

/**
 * Session repository for the sandboxed `mock` flavor.
 *
 * Unlike the hermetic
 * [com.cereal.client.infrastructure.data.repository.inmemory.InMemorySessionRepository] used by UI
 * tests (which keeps its own session state), this delegates session management to the real
 * [UserSession]. That matters for a *running* app: `setSessionUser` opens the Koin `UserScope` and
 * populates the session that the rest of the graph reads from — e.g. [FileSystemScriptRepository],
 * which resolves the current user via `UserSession.requireUser()`. Without it the auth state and
 * the session disagree and script loading throws `UserNotAuthenticatedException`.
 */
class SandboxSessionRepository(
    private val userSession: UserSession,
) : SessionRepository {
    override suspend fun setSessionUser(user: User?) {
        userSession.setUser(user)
    }

    override suspend fun getStoredUser(): User? = null

    override suspend fun getAuthenticatedUserFlow(): Flow<User?> = userSession.getUserFlow()
}

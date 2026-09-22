package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.user.User
import com.cereal.client.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hermetic [SessionRepository] for UI tests and the sandboxed (`mock`) flavor. Touches no data
 * sources. Seeded with a guest user by default.
 */
class InMemorySessionRepository(
    private val user: User = DEFAULT_USER,
) : SessionRepository {
    private val sessionUser = MutableStateFlow<User?>(user)

    override suspend fun setSessionUser(user: User?) {
        sessionUser.value = user
    }

    override suspend fun getStoredUser(): User? = sessionUser.value

    override suspend fun getAuthenticatedUserFlow(): Flow<User?> = sessionUser

    companion object {
        val DEFAULT_USER =
            User(
                id = "test-user",
                name = "Cereal test user",
                email = "noreply@cereal-automation.com",
                encryptionKey = "",
                accessToken = "test-token",
                isGuest = true,
            )
    }
}

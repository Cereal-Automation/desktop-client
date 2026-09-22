package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.user.User
import com.cereal.client.fixtures.FakeMarketplaceDataSource
import com.cereal.client.fixtures.InMemoryKeyValueDataSource
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.auth.UserTokenDataSource
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.User as ApiUser

class SessionRepositoryImplTest {
    private val marketplaceDataSource = FakeMarketplaceDataSource()
    private val keyValueDataSource = InMemoryKeyValueDataSource()
    private val userTokenDataSource = UserTokenDataSource(keyValueDataSource)
    private val userSession = mockk<UserSession>(relaxed = true)
    private lateinit var repository: SessionRepositoryImpl

    @BeforeEach
    fun setUp() {
        repository =
            SessionRepositoryImpl(
                marketplaceDataSource = marketplaceDataSource,
                userSession = userSession,
                userTokenDataSource = userTokenDataSource,
            )
    }

    private fun apiUser(
        id: String = "user-1",
        name: String = "Alice",
        email: String = "alice@example.com",
        key: String = "encryption-key",
        isGuest: Boolean = false,
    ) = ApiUser(id = id, name = name, email = email, key = key, isGuest = isGuest)

    @Test
    fun `getStoredUser returns domain user when a token is stored`() =
        runTest {
            marketplaceDataSource.authenticatedUser = apiUser(id = "u-9")
            // Persist a token through the real token data source so getStoredUser observes it.
            repository.setSessionUser(User("u-9", "n", "e", "k", "stored-token", false))

            val result = repository.getStoredUser()

            assertEquals("u-9", result?.id)
            assertEquals("stored-token", result?.accessToken)
        }

    @Test
    fun `getStoredUser returns null when there is no token`() =
        runTest {
            assertNull(repository.getStoredUser())
        }

    @Test
    fun `setSessionUser persists the access token and sets the session user`() =
        runTest {
            val user = User("u", "n", "e", "k", "access-token", false)

            repository.setSessionUser(user)

            // Token round-trips through the real token data source.
            assertEquals("access-token", userTokenDataSource.loadToken())
            // MockK: auth session edge
            coVerify { userSession.setUser(user) }
        }

    @Test
    fun `setSessionUser with null clears the persisted token`() =
        runTest {
            repository.setSessionUser(User("u", "n", "e", "k", "access-token", false))

            repository.setSessionUser(null)

            assertNull(userTokenDataSource.loadToken())
            // MockK: auth session edge
            coVerify { userSession.setUser(null) }
        }
}

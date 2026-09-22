package com.cereal.client.application.interactor.auth

import com.cereal.client.application.Interactor
import com.cereal.client.application.auth.UserAuthManager
import com.cereal.client.domain.model.user.User
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Mock-based by design: this interactor's only collaborator is [UserAuthManager], an application
 * service (not a repository), whose real construction pulls in six further collaborators. The
 * in-memory-repository preference applies to repository dependencies, of which this interactor has
 * none.
 */
class GetAuthenticatedUserInteractorTest {
    private val userAuthManager = mockk<UserAuthManager>()
    private lateinit var getAuthenticatedUserInteractor: GetAuthenticatedUserInteractor

    @BeforeEach
    fun setup() {
        getAuthenticatedUserInteractor = GetAuthenticatedUserInteractor(userAuthManager)
    }

    @Test
    fun `run emits the authenticated user from UserAuthManager`() =
        runTest {
            // Given
            val user =
                User(
                    id = "user-1",
                    name = "Jane Doe",
                    email = "jane@example.com",
                    encryptionKey = "key",
                    accessToken = "token",
                )
            coEvery { userAuthManager.getAuthenticatedUserFlow() } returns flowOf(user)

            // When
            val result = getAuthenticatedUserInteractor.run(Interactor.None()).first()

            // Then
            assertEquals(user, result)
        }

    @Test
    fun `run emits null when no user is authenticated`() =
        runTest {
            // Given
            coEvery { userAuthManager.getAuthenticatedUserFlow() } returns flowOf(null)

            // When
            val result = getAuthenticatedUserInteractor.run(Interactor.None()).first()

            // Then
            assertNull(result)
        }
}

package com.cereal.client.application.interactor.auth

import com.cereal.client.application.Interactor
import com.cereal.client.application.auth.UserAuthManager
import com.cereal.client.application.auth.UserAuthenticatingState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Mock-based by design: this interactor's only collaborator is [UserAuthManager], an application
 * service (not a repository), whose real construction pulls in six further collaborators. The
 * in-memory-repository preference applies to repository dependencies, of which this interactor has
 * none.
 */
class AuthenticateGuestInteractorTest {
    private val userAuthManager = mockk<UserAuthManager>()
    private lateinit var authenticateGuestInteractor: AuthenticateGuestInteractor

    @BeforeEach
    fun setup() {
        authenticateGuestInteractor = AuthenticateGuestInteractor(userAuthManager)
    }

    @Test
    fun `run delegates to UserAuthManager authenticateGuest`() =
        runTest {
            // Given
            coEvery { userAuthManager.authenticateGuest() } returns
                flowOf(
                    UserAuthenticatingState.InitializingDiscord,
                )

            // When
            authenticateGuestInteractor(Interactor.None())

            // Then
            coVerify { userAuthManager.authenticateGuest() }
        }
}

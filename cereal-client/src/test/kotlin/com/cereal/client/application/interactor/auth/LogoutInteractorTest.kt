package com.cereal.client.application.interactor.auth

import com.cereal.client.application.Interactor
import com.cereal.client.application.auth.UserAuthManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Mock-based by design: this interactor's only collaborator is [UserAuthManager], an application
 * service (not a repository), whose real construction pulls in six further collaborators. The
 * in-memory-repository preference applies to repository dependencies, of which this interactor has
 * none.
 */
class LogoutInteractorTest {
    private val userAuthManager = mockk<UserAuthManager>()
    private lateinit var logoutInteractor: LogoutInteractor

    @BeforeEach
    fun setup() {
        logoutInteractor = LogoutInteractor(userAuthManager)
    }

    @Test
    fun `run delegates to UserAuthManager deauthenticate`() =
        runTest {
            // Given
            coEvery { userAuthManager.deauthenticate() } just Runs

            // When
            logoutInteractor.run(Interactor.None())

            // Then
            coVerify { userAuthManager.deauthenticate() }
        }

    @Test
    fun `run propagates exceptions thrown by UserAuthManager`() =
        runTest {
            // Given
            coEvery { userAuthManager.deauthenticate() } throws IllegalStateException("boom")

            // When / Then
            assertThrows<IllegalStateException> {
                logoutInteractor.run(Interactor.None())
            }
        }
}

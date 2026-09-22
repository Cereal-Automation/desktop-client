package com.cereal.client.application.interactor.auth

import com.cereal.client.application.auth.UserAuthManager
import com.cereal.client.application.auth.UserAuthenticatingState
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Mock-based by design: this interactor's only collaborator is [UserAuthManager], an application
 * service (not a repository), whose real construction pulls in six further collaborators. The
 * in-memory-repository preference applies to repository dependencies, of which this interactor has
 * none.
 */
@OptIn(FlowPreview::class)
class AuthenticateInteractorTest {
    private val userAuthManager = mockk<UserAuthManager>()
    private lateinit var authenticateInteractor: AuthenticateInteractor

    @BeforeEach
    fun setup() {
        authenticateInteractor = AuthenticateInteractor(userAuthManager)
    }

    @Test
    fun `run returns the authentication state flow from UserAuthManager`() =
        runTest {
            // Given
            val username = "user@example.com"
            val password = "p4ssword"
            coEvery { userAuthManager.authenticate(username, password) } returns
                flowOf(UserAuthenticatingState.InitializingDiscord)

            // When
            val emitted =
                authenticateInteractor.run(AuthenticateInteractor.Params(username, password)).toList()

            // Then
            coVerify { userAuthManager.authenticate(username, password) }
            assertEquals(listOf(UserAuthenticatingState.InitializingDiscord), emitted)
        }

    @Test
    fun `invoke wraps the emitted authentication states as successes in order`() =
        runTest {
            // Given
            val username = "user@example.com"
            val password = "p4ssword"
            coEvery { userAuthManager.authenticate(username, password) } returns
                flowOf(
                    UserAuthenticatingState.InitializingDiscord,
                    UserAuthenticatingState.SyncScripts(completed = 1, total = 3),
                    UserAuthenticatingState.RestoreTasks,
                )

            // When
            val results =
                authenticateInteractor(AuthenticateInteractor.Params(username, password)).toList()

            // Then
            assertEquals(
                listOf(
                    UserAuthenticatingState.InitializingDiscord,
                    UserAuthenticatingState.SyncScripts(completed = 1, total = 3),
                    UserAuthenticatingState.RestoreTasks,
                ),
                results.map { (it as SuspendableResult.Success).value },
            )
        }

    @Test
    fun `invoke surfaces an error thrown while collecting as a failure`() =
        runTest {
            // Given
            coEvery { userAuthManager.authenticate(any(), any()) } returns
                flow { throw IllegalStateException("invalid credentials") }

            // When
            val results =
                authenticateInteractor(AuthenticateInteractor.Params("user@example.com", "wrong")).toList()

            // Then
            assertTrue(results.single() is SuspendableResult.Failure)
        }
}

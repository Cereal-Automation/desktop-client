package com.cereal.client.application.interactor.auth

import com.cereal.client.application.auth.UserAuthManager
import com.cereal.client.application.auth.UserAuthenticatingState
import com.cereal.client.domain.model.auth.OAuthProvider
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Mock-based by design: the only collaborator is [UserAuthManager], an application service whose
 * real construction pulls in six further collaborators (see [AuthenticateGuestInteractorTest]).
 */
@OptIn(FlowPreview::class)
class AuthenticateWithOAuthInteractorTest {
    private val userAuthManager = mockk<UserAuthManager>()
    private lateinit var interactor: AuthenticateWithOAuthInteractor

    @BeforeEach
    fun setup() {
        interactor = AuthenticateWithOAuthInteractor(userAuthManager)
    }

    @Test
    fun `run delegates to UserAuthManager authenticateWith the given provider`() =
        runTest {
            coEvery { userAuthManager.authenticateWith(OAuthProvider.DISCORD) } returns
                flowOf(UserAuthenticatingState.InitializingDiscord)

            interactor.run(AuthenticateWithOAuthInteractor.Params(OAuthProvider.DISCORD))

            coVerify { userAuthManager.authenticateWith(OAuthProvider.DISCORD) }
        }

    @Test
    fun `invoke surfaces the authentication states as successful results`() =
        runTest {
            coEvery { userAuthManager.authenticateWith(OAuthProvider.GOOGLE) } returns
                flowOf(UserAuthenticatingState.InitializingDiscord, UserAuthenticatingState.RestoreTasks)

            val results = interactor(AuthenticateWithOAuthInteractor.Params(OAuthProvider.GOOGLE)).toList()

            assertTrue(results.all { it is SuspendableResult.Success })
            assertEquals(2, results.size)
        }

    @Test
    fun `run propagates failures from UserAuthManager`() =
        runTest {
            coEvery { userAuthManager.authenticateWith(OAuthProvider.GOOGLE) } throws IllegalStateException("boom")

            assertThrows<IllegalStateException> {
                interactor.run(AuthenticateWithOAuthInteractor.Params(OAuthProvider.GOOGLE))
            }
        }
}

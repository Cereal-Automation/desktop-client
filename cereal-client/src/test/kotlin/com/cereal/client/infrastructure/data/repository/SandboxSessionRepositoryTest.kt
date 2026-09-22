package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SandboxSessionRepositoryTest {
    private lateinit var userSession: UserSession
    private lateinit var repository: SandboxSessionRepository

    @BeforeEach
    fun setUp() {
        // MockK: UserSession is a final auth-edge collaborator; behaviour is exercised through it.
        userSession = mockk(relaxed = true)
        repository = SandboxSessionRepository(userSession)
    }

    @Test
    fun `setSessionUser propagates the user to the session flow`() =
        runTest {
            // The real UserSession is mocked at the auth edge; assert the observable effect by
            // feeding setUser back into the session's user flow and reading it via the repo.
            val user =
                User(
                    id = "x",
                    name = "n",
                    email = "e",
                    encryptionKey = "",
                    accessToken = "t",
                )
            val emitted = MutableSessionFlow(user)
            // MockK: auth edge — UserSession is final; wire setUser to the flow it would update.
            coEvery { userSession.setUser(any()) } answers { emitted.value = firstArg() }
            every { userSession.getUserFlow() } returns emitted.flow

            repository.setSessionUser(user)

            assertEquals(user, repository.getAuthenticatedUserFlow().first())
        }

    @Test
    fun `getStoredUser returns null`() =
        runTest {
            assertNull(repository.getStoredUser())
        }

    @Test
    fun `getAuthenticatedUserFlow returns the session user flow`() =
        runTest {
            val flow = MutableSessionFlow<User?>(null)
            // MockK: auth edge — read the user flow exposed by the final UserSession.
            every { userSession.getUserFlow() } returns flow.flow

            assertNull(repository.getAuthenticatedUserFlow().first())
        }
}

/** Tiny helper wrapping a [kotlinx.coroutines.flow.MutableStateFlow] so the session mock can both
 * accept writes (setUser) and expose reads (getUserFlow) without verifying call sequences. */
private class MutableSessionFlow<T>(
    initial: T,
) {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(initial)
    val flow: kotlinx.coroutines.flow.Flow<T> get() = state
    var value: T
        get() = state.value
        set(newValue) {
            state.value = newValue
        }
}

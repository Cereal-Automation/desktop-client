package com.cereal.client.presentation.authenticate.registration

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.auth.GetAuthenticatedUserInteractor
import com.cereal.client.application.interactor.auth.RegisterInteractor
import com.cereal.client.domain.model.user.User
import com.cereal.client.presentation.model.LoadState
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationViewModelTest {
    private val registerInteractor: RegisterInteractor = mockk(relaxed = true)
    private val getAuthenticatedUserInteractor: GetAuthenticatedUserInteractor = mockk(relaxed = true)
    private lateinit var dispatcherProvider: CoroutinesDispatcherProvider

    @BeforeEach
    fun setUp() {
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        dispatcherProvider = CoroutinesDispatcherProvider(testDispatcher, testDispatcher, testDispatcher)
        coEvery { getAuthenticatedUserInteractor(any()) } returns flowOf(SuspendableResult.Failure(Exception("no user")))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = RegistrationViewModel(dispatcherProvider, registerInteractor, getAuthenticatedUserInteractor)

    private fun sampleUser(isGuest: Boolean = false) = User(id = "1", name = "Jane", email = "jane@example.com", encryptionKey = "k", accessToken = "t", isGuest = isGuest)

    @Test
    fun `updatePassword recomputes password strength`() {
        val viewModel = createViewModel()

        viewModel.updatePassword("Password1")

        assertEquals("Password1", viewModel.password.value)
        assertTrue(viewModel.passwordStrength.value.isValid)
    }

    @Test
    fun `register sets error when password is not valid`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.updatePassword("weak")

            viewModel.register()

            assertTrue(viewModel.loadingState.value is LoadState.Error)
        }

    @Test
    fun `register emits success and authenticates when registration succeeds`() =
        runTest {
            val viewModel = createViewModel()
            val events = mutableListOf<Unit>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.registrationSuccess.collect { events.add(it) } }

            val callback = slot<suspend (SuspendableResult<Unit, Exception>) -> Unit>()
            coEvery { registerInteractor(any(), capture(callback)) } coAnswers {
                callback.captured(SuspendableResult.Success(Unit))
            }

            viewModel.name.value = "Jane"
            viewModel.username.value = "jane@example.com"
            viewModel.updatePassword("Password1")
            viewModel.register()

            assertTrue(viewModel.isAuthenticated.value == true)
            assertTrue(viewModel.loadingState.value is LoadState.NotLoading)
            assertEquals(1, events.size)

            job.cancel()
        }

    @Test
    fun `register sets error state when registration fails`() =
        runTest {
            val viewModel = createViewModel()
            val callback = slot<suspend (SuspendableResult<Unit, Exception>) -> Unit>()
            coEvery { registerInteractor(any(), capture(callback)) } coAnswers {
                callback.captured(SuspendableResult.Failure(Exception("boom")))
            }

            viewModel.updatePassword("Password1")
            viewModel.register()

            assertTrue(viewModel.loadingState.value is LoadState.Error)
        }

    @Test
    fun `register forwards name username and password to the interactor`() =
        runTest {
            val viewModel = createViewModel()
            val params = slot<RegisterInteractor.Params>()
            coEvery { registerInteractor(capture(params), any()) } coAnswers {
                secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(SuspendableResult.Success(Unit))
            }

            viewModel.name.value = "Jane Doe"
            viewModel.username.value = "jane@example.com"
            viewModel.updatePassword("Password1")
            viewModel.register()

            assertEquals("Jane Doe", params.captured.name)
            assertEquals("jane@example.com", params.captured.email)
            assertEquals("Password1", params.captured.password)
        }

    @Test
    fun `init exposes the authenticated user`() {
        coEvery { getAuthenticatedUserInteractor(any()) } returns flowOf(SuspendableResult.Success(sampleUser()))

        val viewModel = createViewModel()

        assertEquals(sampleUser(), viewModel.user.value)
        assertTrue(viewModel.isAuthenticated.value == true)
    }

    @Test
    fun `register is a no-op for an already authenticated non-guest user`() =
        runTest {
            coEvery { getAuthenticatedUserInteractor(any()) } returns flowOf(SuspendableResult.Success(sampleUser()))
            val viewModel = createViewModel()
            viewModel.updatePassword("Password1")

            viewModel.register()

            // State stays as the init-derived NotLoading; no registration attempted.
            assertTrue(viewModel.loadingState.value is LoadState.NotLoading)
            assertFalse(viewModel.loadingState.value is LoadState.Loading)
        }
}

package com.cereal.client.presentation.authenticate.login

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.auth.UserAuthenticatingState
import com.cereal.client.application.interactor.auth.AuthenticateGuestInteractor
import com.cereal.client.application.interactor.auth.AuthenticateInteractor
import com.cereal.client.application.interactor.auth.AuthenticateWithOAuthInteractor
import com.cereal.client.application.interactor.auth.GetAuthenticatedUserInteractor
import com.cereal.client.domain.model.auth.OAuthProvider
import com.cereal.client.presentation.model.LoadState
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(FlowPreview::class)
@ExperimentalCoroutinesApi
class LoginViewModelTest {
    private val dispatcherProvider = mockk<CoroutinesDispatcherProvider>()
    private val authenticateInteractor = mockk<AuthenticateInteractor>()
    private val authenticateGuestInteractor = mockk<AuthenticateGuestInteractor>()
    private val getAuthenticatedUserInteractor = mockk<GetAuthenticatedUserInteractor>()
    private val authenticateWithOAuthInteractor = mockk<AuthenticateWithOAuthInteractor>()

    private lateinit var viewModel: LoginViewModel

    @BeforeEach
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        coEvery { dispatcherProvider.main } returns testDispatcher
        coEvery { dispatcherProvider.io } returns testDispatcher

        // Mock initial user state
        coEvery { getAuthenticatedUserInteractor(any()) } returns flowOf(SuspendableResult.of(null))

        viewModel =
            LoginViewModel(
                TestScope(testDispatcher),
                dispatcherProvider,
                authenticateInteractor,
                authenticateGuestInteractor,
                getAuthenticatedUserInteractor,
                authenticateWithOAuthInteractor,
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `login emits success event when authentication succeeds`() =
        runTest {
            // Given
            val emittedEvents = mutableListOf<Unit>()
            val collectionJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.loginSuccess.collect { emittedEvents.add(it) }
                }

            val state: UserAuthenticatingState = UserAuthenticatingState.RestoreTasks
            coEvery { authenticateInteractor(any()) } returns flowOf(SuspendableResult.of(state))

            viewModel.onUsernameChanged("user")
            viewModel.onPasswordChanged("pass")

            // When
            viewModel.login()

            // Then
            assertTrue(viewModel.isAuthenticated.value == true)
            assertTrue(viewModel.loadingState.value is LoadState.NotLoading)
            assertEquals(1, emittedEvents.size)

            collectionJob.cancel()
        }

    @Test
    fun `loginWithDiscord drives the OAuth interactor with the Discord provider`() =
        runTest {
            val emittedEvents = mutableListOf<Unit>()
            val collectionJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.loginSuccess.collect { emittedEvents.add(it) }
                }

            val state: UserAuthenticatingState = UserAuthenticatingState.RestoreTasks
            coEvery { authenticateWithOAuthInteractor(any()) } returns flowOf(SuspendableResult.of(state))

            // When
            viewModel.loginWithDiscord()

            // Then
            coVerify { authenticateWithOAuthInteractor(AuthenticateWithOAuthInteractor.Params(OAuthProvider.DISCORD)) }
            assertTrue(viewModel.isAuthenticated.value == true)
            assertTrue(viewModel.loadingState.value is LoadState.NotLoading)
            assertEquals(1, emittedEvents.size)

            collectionJob.cancel()
        }

    @Test
    fun `loginAsGuest emits success event when authentication succeeds`() =
        runTest {
            // Given
            val emittedEvents = mutableListOf<Unit>()
            val collectionJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.loginSuccess.collect { emittedEvents.add(it) }
                }

            val callbackSlot = slot<suspend (SuspendableResult<Unit, Exception>) -> Unit>()
            coEvery { authenticateGuestInteractor(any(), capture(callbackSlot)) } coAnswers {
                callbackSlot.captured(SuspendableResult.of(Unit))
            }

            // When
            viewModel.loginAsGuest()

            // Then
            assertTrue(viewModel.isAuthenticated.value == true)
            assertTrue(viewModel.loadingState.value is LoadState.NotLoading)
            assertEquals(1, emittedEvents.size)

            collectionJob.cancel()
        }
}

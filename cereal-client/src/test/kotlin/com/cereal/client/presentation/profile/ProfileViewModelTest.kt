package com.cereal.client.presentation.profile

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.auth.GetAuthenticatedUserInteractor
import com.cereal.client.application.interactor.auth.LogoutInteractor
import com.cereal.client.domain.model.user.User
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    private val getUserInteractor: GetAuthenticatedUserInteractor = mockk(relaxed = true)
    private val logoutInteractor: LogoutInteractor = mockk(relaxed = true)

    private lateinit var viewModel: ProfileViewModel

    private fun sampleUser() =
        User(
            id = "1",
            name = "Jane",
            email = "jane@example.com",
            encryptionKey = "key",
            accessToken = "token",
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { getUserInteractor(any()) } returns flowOf(SuspendableResult.Success(sampleUser()))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel = ProfileViewModel(CoroutineScope(dispatcher), dispatcherProvider, getUserInteractor, logoutInteractor)
    }

    @Test
    fun `init loads the authenticated user into state`() {
        createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(sampleUser(), viewModel.user.value)
    }

    @Test
    fun `init leaves user null when result is a failure`() {
        coEvery { getUserInteractor(any()) } returns flowOf(SuspendableResult.Failure(Exception("no user")))
        createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.user.value)
    }

    @Test
    fun `logout invokes the logout interactor`() {
        createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.logout()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { logoutInteractor(any(), any()) }
    }
}

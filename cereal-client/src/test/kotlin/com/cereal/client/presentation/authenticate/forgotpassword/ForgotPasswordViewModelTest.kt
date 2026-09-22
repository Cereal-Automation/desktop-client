package com.cereal.client.presentation.authenticate.forgotpassword

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.auth.ForgotPasswordInteractor
import com.cereal.client.presentation.model.LoadState
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {
    private val forgotPasswordInteractor: ForgotPasswordInteractor = mockk(relaxed = true)
    private lateinit var dispatcherProvider: CoroutinesDispatcherProvider
    private lateinit var viewModel: ForgotPasswordViewModel

    @BeforeEach
    fun setUp() {
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        dispatcherProvider = CoroutinesDispatcherProvider(testDispatcher, testDispatcher, testDispatcher)
        viewModel = ForgotPasswordViewModel(dispatcherProvider, forgotPasswordInteractor)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submit does nothing when email is blank`() =
        runTest {
            viewModel.submit()

            assertTrue(viewModel.loadingState.value is LoadState.NotLoading)
            coVerify(exactly = 0) { forgotPasswordInteractor(any(), any()) }
        }

    @Test
    fun `submit emits success event and resets loading on success`() =
        runTest {
            val events = mutableListOf<String>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.successEvent.collect { events.add(it) } }

            val callback = slot<suspend (SuspendableResult<Unit, Exception>) -> Unit>()
            coEvery { forgotPasswordInteractor(any(), capture(callback)) } coAnswers {
                callback.captured(SuspendableResult.Success(Unit))
            }

            viewModel.email.value = "user@example.com"
            viewModel.submit()

            assertTrue(viewModel.loadingState.value is LoadState.NotLoading)
            assertEquals(listOf("user@example.com"), events)

            job.cancel()
        }

    @Test
    fun `submit sets error state on failure`() =
        runTest {
            val callback = slot<suspend (SuspendableResult<Unit, Exception>) -> Unit>()
            coEvery { forgotPasswordInteractor(any(), capture(callback)) } coAnswers {
                callback.captured(SuspendableResult.Failure(Exception("boom")))
            }

            viewModel.email.value = "user@example.com"
            viewModel.submit()

            assertTrue(viewModel.loadingState.value is LoadState.Error)
        }

    @Test
    fun `submit forwards the entered email to the interactor`() =
        runTest {
            val params = slot<ForgotPasswordInteractor.Params>()
            coEvery { forgotPasswordInteractor(capture(params), any()) } coAnswers {
                secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(SuspendableResult.Success(Unit))
            }

            viewModel.email.value = "person@example.com"
            viewModel.submit()

            assertEquals("person@example.com", params.captured.email)
        }
}

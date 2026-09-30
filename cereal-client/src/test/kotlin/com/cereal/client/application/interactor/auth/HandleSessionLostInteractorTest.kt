package com.cereal.client.application.interactor.auth

import com.cereal.client.application.Interactor
import com.cereal.client.application.auth.UserAuthManager
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.provider.SessionLostProviderImpl
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HandleSessionLostInteractorTest {
    @Test
    fun `a failing stop still clears the session, and later losses are still handled`() =
        runTest {
            val sessionLost = SessionLostProviderImpl()
            val userAuthManager = mockk<UserAuthManager>(relaxed = true)
            coEvery { userAuthManager.getAuthenticatedUserFlow() } returns flowOf(User("u", "n", "e", "k", "t"))
            val taskManager = mockk<TaskManager>(relaxed = true)
            coEvery { taskManager.stopAllTasks(any()) } throws IllegalStateException("boom")
            val interactor =
                HandleSessionLostInteractor(sessionLost, userAuthManager, taskManager, mockk(relaxed = true), mockk(relaxed = true))
            val results = mutableListOf<SuspendableResult<Unit, Exception>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { interactor(Interactor.None()).collect { results += it } }

            sessionLost.report()
            advanceUntilIdle()
            sessionLost.report()
            advanceUntilIdle()

            assertEquals(2, results.size)
            assertTrue(results.all { it is SuspendableResult.Success }, results.toString())
            coVerify(exactly = 2) { userAuthManager.deauthenticate() }
        }
}

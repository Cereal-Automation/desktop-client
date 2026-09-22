package com.cereal.client.infrastructure.data.repository

import com.cereal.client.application.exception.UserNotAuthenticatedException
import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.logging.LoggingPriority
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.LogEventDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class LogEventRepositoryImplTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val logEventDataSource = mockk<LogEventDataSource>(relaxed = true)
    private val userSession = mockk<UserSession>()

    private val repo = LogEventRepositoryImpl(logEventDataSource, userSession, testScope)

    private fun logEvent(
        priority: LoggingPriority = LoggingPriority.INFO,
        tag: String = "com.pkg",
        message: String = "hello",
    ) = LoggingEvent(priority, tag, message, Date())

    @Test
    fun `observeLogEvents emits empty list when user is null`() =
        testScope.runTest {
            every { userSession.getUserFlow() } returns MutableStateFlow(null)

            val result = repo.observeLogEvents("com.pkg").first()

            assertEquals(emptyList<LoggingEvent>(), result)
        }

    @Test
    fun `observeLogEvents delegates to data source when user is logged in`() =
        testScope.runTest {
            val user = mockk<User>()
            every { userSession.getUserFlow() } returns MutableStateFlow(user)
            val events = listOf(LoggingEvent(LoggingPriority.INFO, "com.pkg", "hello", Date()))
            every { logEventDataSource.observeLogEvents(user, "com.pkg") } returns flowOf(events)

            val result = repo.observeLogEvents("com.pkg").first()

            assertEquals(events, result)
        }

    @Test
    fun `observeLogEvents switches to empty list when user logs out`() =
        testScope.runTest {
            val user = mockk<User>()
            val userFlow = MutableStateFlow<User?>(user)
            every { userSession.getUserFlow() } returns userFlow
            val events = listOf(LoggingEvent(LoggingPriority.INFO, "com.pkg", "hello", Date()))
            every { logEventDataSource.observeLogEvents(user, "com.pkg") } returns flowOf(events)

            val results = mutableListOf<List<LoggingEvent>>()
            val job = launch { repo.observeLogEvents("com.pkg").collect { results.add(it) } }
            advanceUntilIdle()

            userFlow.emit(null)
            advanceUntilIdle()

            assertEquals(emptyList<LoggingEvent>(), results.last())
            job.cancel()
        }

    @Test
    fun `persist inserts the event to the data source for the current user`() =
        testScope.runTest {
            val user = mockk<User>()
            coEvery { userSession.requireUser() } returns user

            repo.persist(logEvent(priority = LoggingPriority.INFO, message = "hello"))
            advanceUntilIdle()

            coVerify {
                logEventDataSource.insertLogEvent(user, "com.pkg", LoggingPriority.INFO, "hello", any())
            }
        }

    @Test
    fun `persist is skipped silently when user not authenticated`() =
        testScope.runTest {
            coEvery { userSession.requireUser() } throws UserNotAuthenticatedException()

            repo.persist(logEvent())
            advanceUntilIdle()

            coVerify(exactly = 0) {
                logEventDataSource.insertLogEvent(any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `persist error is swallowed without propagating`() =
        testScope.runTest {
            val user = mockk<User>()
            coEvery { userSession.requireUser() } returns user
            coEvery {
                logEventDataSource.insertLogEvent(any(), any(), any(), any(), any())
            } throws RuntimeException("db error")

            repo.persist(logEvent())
            advanceUntilIdle()
            // No exception thrown — test passes by reaching this line
        }
}

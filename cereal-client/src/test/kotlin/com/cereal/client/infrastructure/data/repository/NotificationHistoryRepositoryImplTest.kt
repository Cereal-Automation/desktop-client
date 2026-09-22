package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.model.notification.NotificationChannelType
import com.cereal.client.domain.model.notification.NotificationDeliveryStatus
import com.cereal.client.domain.model.user.User
import com.cereal.client.fixtures.InMemoryNotificationHistoryDataSource
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.NotificationHistoryDataSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationHistoryRepositoryImplTest {
    private val dataSource = InMemoryNotificationHistoryDataSource()

    // MockK: UserSession is a concrete final class; the stubs below only supply the
    // authenticated user as a precondition (not call-sequence verification).
    private val userSession = mockk<UserSession>(relaxed = true)
    private lateinit var repository: NotificationHistoryRepositoryImpl

    private val user =
        User(
            id = "user-1",
            name = "Test User",
            email = "test@example.com",
            encryptionKey = "",
            accessToken = "token",
        )

    private val attempt =
        ChannelAttempt(
            channel = NotificationChannelType.SYSTEM,
            status = NotificationDeliveryStatus.SUCCESS,
            payload = null,
            errorMessage = null,
        )

    @BeforeEach
    fun setUp() {
        coEvery { userSession.requireUser() } returns user
        every { userSession.getUserFlow() } returns flowOf(user)
        repository = NotificationHistoryRepositoryImpl(dataSource, userSession)
    }

    @Test
    fun `record with attempts is observable via observeByTaskId`() =
        runTest {
            repository.record(
                taskId = "task-1",
                title = "title",
                message = "message",
                timestamp = 42L,
                attempts = listOf(attempt),
            )

            val recorded = repository.observeByTaskId("task-1").first()

            assertEquals(1, recorded.size)
            assertEquals("message", recorded.first().message)
            assertEquals("title", recorded.first().title)
        }

    @Test
    fun `record with empty attempts records nothing`() =
        runTest {
            repository.record(
                taskId = "task-1",
                title = "title",
                message = "message",
                timestamp = 1L,
                attempts = emptyList(),
            )

            assertTrue(repository.observeByTaskId("task-1").first().isEmpty())
        }

    @Test
    fun `observeByTaskId emits empty list when no user is authenticated`() =
        runTest {
            every { userSession.getUserFlow() } returns flowOf(null)

            assertEquals(emptyList(), repository.observeByTaskId("task-1").first())
        }

    @Test
    fun `observeAttempts reflects the attempts recorded for a notification`() =
        runTest {
            repository.record(
                taskId = "task-1",
                title = "title",
                message = "message",
                timestamp = 42L,
                attempts = listOf(attempt),
            )

            val notificationId =
                repository
                    .observeByTaskId("task-1")
                    .first()
                    .first()
                    .id
            val attempts = repository.observeAttempts(notificationId).first()

            assertEquals(1, attempts.size)
            assertEquals(NotificationChannelType.SYSTEM, attempts.first().channel)
        }

    @Test
    fun `observeAttempts emits empty list when no user is authenticated`() =
        runTest {
            every { userSession.getUserFlow() } returns flowOf(null)

            assertEquals(emptyList(), repository.observeAttempts("n-1").first())
        }

    @Test
    fun `pruneOlderThan removes only notifications older than the cutoff`() =
        runTest {
            repository.record(
                taskId = "task-1",
                title = "old",
                message = "old message",
                timestamp = 100L,
                attempts = listOf(attempt),
            )
            repository.record(
                taskId = "task-1",
                title = "new",
                message = "new message",
                timestamp = 300L,
                attempts = listOf(attempt),
            )

            repository.pruneOlderThan(cutoffMillis = 200L)

            val remaining = repository.observeByTaskId("task-1").first()
            assertEquals(1, remaining.size)
            assertEquals("new message", remaining.first().message)
        }

    @Test
    fun `record swallows exception when requireUser throws`() =
        runTest {
            // MockK: inject failure to verify it is swallowed
            val failingSession = mockk<UserSession>(relaxed = true)
            coEvery { failingSession.requireUser() } throws RuntimeException("not authenticated")
            val repo = NotificationHistoryRepositoryImpl(dataSource, failingSession)

            // Should not propagate the exception.
            repo.record(
                taskId = "task-1",
                title = null,
                message = "message",
                timestamp = 1L,
                attempts = listOf(attempt),
            )
        }

    @Test
    fun `pruneOlderThan swallows exception from datasource`() =
        runTest {
            // MockK: inject failure to verify it is swallowed
            val failingDataSource = mockk<NotificationHistoryDataSource>(relaxed = true)
            coEvery { failingDataSource.pruneOlderThan(user, 1000L) } throws RuntimeException("db error")
            val repo = NotificationHistoryRepositoryImpl(failingDataSource, userSession)

            // Should not propagate the exception.
            repo.pruneOlderThan(1000L)
        }
}

package com.cereal.client.infrastructure.data.datasource.database.integration

import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.model.notification.NotificationChannelType
import com.cereal.client.domain.model.notification.NotificationDeliveryStatus
import com.cereal.client.infrastructure.data.datasource.database.room.RoomNotificationHistoryDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Integration test for [RoomNotificationHistoryDataSource] against a real (in-memory backed) Room
 * database.
 *
 * Seam under test: the Room data source ↔ SQLite, including field-level [EncryptedString]
 * [com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString] columns
 * (title/message/payload) which round-trip through the real type converters. Only
 * [com.cereal.client.infrastructure.data.datasource.filesystem.security.Encryption] is mocked.
 * See AGENTS.md → "Test at the architecture boundaries".
 */
class RoomNotificationHistoryDataSourceIntegrationTest : RoomUserDatabaseTestBase() {
    private val dataSource by lazy { RoomNotificationHistoryDataSource(roomDatabases) }

    private fun attempt(
        channel: NotificationChannelType = NotificationChannelType.DISCORD,
        status: NotificationDeliveryStatus = NotificationDeliveryStatus.SUCCESS,
        payload: String? = "payload",
        errorMessage: String? = null,
    ) = ChannelAttempt(channel = channel, status = status, payload = payload, errorMessage = errorMessage)

    @Test
    fun `recorded notification is observable with all fields round-tripped`() =
        runTest {
            val taskId = seedTask()

            dataSource.record(
                user = testUser,
                taskId = taskId,
                title = "Title",
                message = "A message",
                timestamp = 1_000L,
                attempts = listOf(attempt()),
            )

            val history = dataSource.observeByTaskId(testUser, taskId).first()
            assertEquals(1, history.size)
            val notification = history.first()
            assertEquals(taskId, notification.taskId)
            assertEquals("Title", notification.title)
            assertEquals("A message", notification.message)
            assertEquals(1_000L, notification.timestamp)
        }

    @Test
    fun `recording with no attempts persists nothing`() =
        runTest {
            val taskId = seedTask()

            dataSource.record(
                user = testUser,
                taskId = taskId,
                title = "Title",
                message = "A message",
                timestamp = 1_000L,
                attempts = emptyList(),
            )

            assertTrue(dataSource.observeByTaskId(testUser, taskId).first().isEmpty())
        }

    @Test
    fun `null title round-trips as null`() =
        runTest {
            val taskId = seedTask()

            dataSource.record(testUser, taskId, title = null, message = "msg", timestamp = 1L, attempts = listOf(attempt()))

            val notification = dataSource.observeByTaskId(testUser, taskId).first().single()
            assertNull(notification.title)
            assertEquals("msg", notification.message)
        }

    @Test
    fun `every channel and delivery status maps correctly through the database`() =
        runTest {
            val taskId = seedTask()
            val attempts =
                listOf(
                    attempt(NotificationChannelType.DISCORD, NotificationDeliveryStatus.SUCCESS, payload = "d"),
                    attempt(NotificationChannelType.TELEGRAM, NotificationDeliveryStatus.FAILURE, payload = null, errorMessage = "boom"),
                    attempt(NotificationChannelType.EMAIL, NotificationDeliveryStatus.SUCCESS, payload = "e"),
                    attempt(NotificationChannelType.SYSTEM, NotificationDeliveryStatus.FAILURE, payload = "s", errorMessage = "err"),
                )

            dataSource.record(testUser, taskId, "T", "M", timestamp = 1L, attempts = attempts)

            val notificationId =
                dataSource
                    .observeByTaskId(testUser, taskId)
                    .first()
                    .single()
                    .id
            val persisted = dataSource.observeAttempts(testUser, notificationId).first()

            // Preserved in send order via the stored position.
            assertEquals(
                listOf(
                    NotificationChannelType.DISCORD,
                    NotificationChannelType.TELEGRAM,
                    NotificationChannelType.EMAIL,
                    NotificationChannelType.SYSTEM,
                ),
                persisted.map { it.channel },
            )
            assertEquals(
                listOf(
                    NotificationDeliveryStatus.SUCCESS,
                    NotificationDeliveryStatus.FAILURE,
                    NotificationDeliveryStatus.SUCCESS,
                    NotificationDeliveryStatus.FAILURE,
                ),
                persisted.map { it.status },
            )
            val telegram = persisted[1]
            assertNull(telegram.payload)
            assertEquals("boom", telegram.errorMessage)
            assertEquals("e", persisted[2].payload)
            assertNull(persisted[2].errorMessage)
        }

    @Test
    fun `attempts carry their parent notification id and span the recorded values`() =
        runTest {
            val taskId = seedTask()
            dataSource.record(testUser, taskId, "T", "M", timestamp = 42L, attempts = listOf(attempt()))

            val notification = dataSource.observeByTaskId(testUser, taskId).first().single()
            val attemptRow = dataSource.observeAttempts(testUser, notification.id).first().single()

            assertEquals(notification.id, attemptRow.notificationId)
            assertEquals("payload", attemptRow.payload)
            assertEquals(42L, attemptRow.timestamp)
        }

    @Test
    fun `notifications are returned newest first and isolated per task`() =
        runTest {
            val taskA = seedTask()
            val taskB = seedTask()

            dataSource.record(testUser, taskA, "old", "old", timestamp = 100L, attempts = listOf(attempt()))
            dataSource.record(testUser, taskA, "new", "new", timestamp = 300L, attempts = listOf(attempt()))
            dataSource.record(testUser, taskB, "other", "other", timestamp = 200L, attempts = listOf(attempt()))

            val historyA = dataSource.observeByTaskId(testUser, taskA).first()
            assertEquals(listOf("new", "old"), historyA.map { it.message })

            val historyB = dataSource.observeByTaskId(testUser, taskB).first()
            assertEquals(listOf("other"), historyB.map { it.message })
        }

    @Test
    fun `pruneOlderThan removes notifications older than the cutoff and cascades to attempts`() =
        runTest {
            val taskId = seedTask()
            dataSource.record(testUser, taskId, "old", "old", timestamp = 100L, attempts = listOf(attempt()))
            dataSource.record(testUser, taskId, "new", "new", timestamp = 300L, attempts = listOf(attempt()))

            val oldId =
                dataSource
                    .observeByTaskId(testUser, taskId)
                    .first()
                    .single { it.message == "old" }
                    .id

            dataSource.pruneOlderThan(testUser, cutoffMillis = 200L)

            val remaining = dataSource.observeByTaskId(testUser, taskId).first()
            assertEquals(listOf("new"), remaining.map { it.message })
            // FK cascade should have removed the pruned notification's attempts.
            assertTrue(dataSource.observeAttempts(testUser, oldId).first().isEmpty())
        }

    @Test
    fun `observeRecent returns notifications across all tasks newest-first`() =
        runTest {
            val taskA = seedTask()
            val taskB = seedTask()

            dataSource.record(testUser, taskA, "a-old", "a-old", timestamp = 100L, attempts = listOf(attempt()))
            dataSource.record(testUser, taskB, "b-newest", "b-newest", timestamp = 300L, attempts = listOf(attempt()))
            dataSource.record(testUser, taskA, "a-mid", "a-mid", timestamp = 200L, attempts = listOf(attempt()))

            val recent = dataSource.observeRecent(testUser, limit = 10).first()

            assertEquals(listOf("b-newest", "a-mid", "a-old"), recent.map { it.message })
        }

    @Test
    fun `observeRecent honours the limit keeping only the newest`() =
        runTest {
            val taskId = seedTask()
            dataSource.record(testUser, taskId, "t1", "t1", timestamp = 100L, attempts = listOf(attempt()))
            dataSource.record(testUser, taskId, "t2", "t2", timestamp = 200L, attempts = listOf(attempt()))
            dataSource.record(testUser, taskId, "t3", "t3", timestamp = 300L, attempts = listOf(attempt()))

            val recent = dataSource.observeRecent(testUser, limit = 2).first()

            assertEquals(listOf("t3", "t2"), recent.map { it.message })
        }
}

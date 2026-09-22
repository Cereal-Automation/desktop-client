package com.cereal.client.infrastructure.data.datasource.database.integration

import com.cereal.client.domain.model.logging.LoggingPriority
import com.cereal.client.infrastructure.data.datasource.database.room.RoomLogEventDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date

/**
 * Integration test for [RoomLogEventDataSource] against a real (in-memory backed) Room database.
 *
 * Seam under test: the Room data source ↔ SQLite. A real [RoomLogEventDataSource] talks to a real
 * SQLite database; only [com.cereal.client.infrastructure.data.datasource.filesystem.security.Encryption]
 * is mocked (an external edge). See AGENTS.md → "Test at the architecture boundaries".
 */
class RoomLogEventDataSourceIntegrationTest : RoomUserDatabaseTestBase() {
    private val dataSource by lazy { RoomLogEventDataSource(roomDatabases) }

    @Test
    fun `inserted log event is observable with all fields round-tripped`() =
        runTest {
            val taskId = seedTask()
            val timestamp = Date(1_000L)

            dataSource.insertLogEvent(testUser, taskId, LoggingPriority.INFO, "hello world", timestamp)

            val events = dataSource.observeLogEvents(testUser, taskId).first()
            assertEquals(1, events.size)
            val event = events.first()
            assertEquals(LoggingPriority.INFO, event.priority)
            assertEquals(taskId, event.tag)
            assertEquals("hello world", event.message)
            assertEquals(timestamp, event.timestamp)
        }

    @Test
    fun `all logging priorities round-trip through the database`() =
        runTest {
            val taskId = seedTask()

            LoggingPriority.entries.forEachIndexed { index, priority ->
                dataSource.insertLogEvent(
                    testUser,
                    taskId,
                    priority,
                    "message-$priority",
                    Date(index.toLong()),
                )
            }

            val priorities = dataSource.observeLogEvents(testUser, taskId).first().map { it.priority }
            assertEquals(LoggingPriority.entries.toSet(), priorities.toSet())
        }

    @Test
    fun `events are returned ordered by timestamp ascending`() =
        runTest {
            val taskId = seedTask()

            dataSource.insertLogEvent(testUser, taskId, LoggingPriority.INFO, "third", Date(300L))
            dataSource.insertLogEvent(testUser, taskId, LoggingPriority.INFO, "first", Date(100L))
            dataSource.insertLogEvent(testUser, taskId, LoggingPriority.INFO, "second", Date(200L))

            val messages = dataSource.observeLogEvents(testUser, taskId).first().map { it.message }
            assertEquals(listOf("first", "second", "third"), messages)
        }

    @Test
    fun `log events are isolated per task`() =
        runTest {
            val taskA = seedTask()
            val taskB = seedTask()

            dataSource.insertLogEvent(testUser, taskA, LoggingPriority.INFO, "for A", Date(1L))
            dataSource.insertLogEvent(testUser, taskB, LoggingPriority.ERROR, "for B", Date(2L))

            val eventsA = dataSource.observeLogEvents(testUser, taskA).first()
            val eventsB = dataSource.observeLogEvents(testUser, taskB).first()

            assertEquals(listOf("for A"), eventsA.map { it.message })
            assertEquals(listOf("for B"), eventsB.map { it.message })
        }

    @Test
    fun `oldest events are pruned once the per-task cap is exceeded`() =
        runTest {
            val taskId = seedTask()
            val total = 505

            // Timestamps 1..505; the prune query keeps the 500 most recent, dropping timestamps 1..5.
            (1..total).forEach { i ->
                dataSource.insertLogEvent(testUser, taskId, LoggingPriority.DEBUG, "event-$i", Date(i.toLong()))
            }

            val events = dataSource.observeLogEvents(testUser, taskId).first()
            assertEquals(500, events.size)
            // Returned ascending, so the earliest surviving event is timestamp 6.
            assertEquals(Date(6L), events.first().timestamp)
            assertTrue(events.none { it.timestamp.time <= 5L }, "Events older than the cap should be pruned")
        }

    @Test
    fun `observing a task with no events returns an empty list`() =
        runTest {
            val taskId = seedTask()

            val events = dataSource.observeLogEvents(testUser, taskId).first()

            assertTrue(events.isEmpty())
        }
}

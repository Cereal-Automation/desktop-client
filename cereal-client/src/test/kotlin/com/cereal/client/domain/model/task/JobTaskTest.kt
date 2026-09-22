package com.cereal.client.domain.model.task

import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class JobTaskTest {
    private val ts = Instant.fromEpochMilliseconds(0)

    private fun createTask(statusHistory: List<TaskStatus> = emptyList()): JobTask =
        JobTask(
            id = "task-1",
            scriptInstance = mockk(relaxed = true),
            configuration = mockk(relaxed = true),
            statusHistory = statusHistory,
            createdAt = Instant.fromEpochMilliseconds(0),
        )

    @Test
    fun `withStatus appends status to empty history`() {
        val task = createTask()

        val result = task.withStatus(TaskStatus.Running("running", ts))

        assertEquals(1, result.statusHistory.size)
        assertEquals("running", result.statusHistory.last().message)
    }

    @Test
    fun `withStatus appends status to existing history`() {
        val task = createTask(listOf(TaskStatus.Running("first", ts)))

        val result = task.withStatus(TaskStatus.Running("second", ts))

        assertEquals(2, result.statusHistory.size)
        assertEquals("second", result.statusHistory.last().message)
    }

    @Test
    fun `withStatus returns same instance when message and type match the last status`() {
        val task = createTask(listOf(TaskStatus.Running("same", ts)))

        val result = task.withStatus(TaskStatus.Running("same", ts))

        assertSame(task, result)
    }

    @Test
    fun `withStatus appends when message matches but type differs`() {
        val task = createTask(listOf(TaskStatus.Running("same", ts)))

        val result = task.withStatus(TaskStatus.Success("same", ts))

        assertEquals(2, result.statusHistory.size)
        assertTrue(result.statusHistory.last() is TaskStatus.Success)
    }

    @Test
    fun `withStatus appends when type matches but message differs`() {
        val task = createTask(listOf(TaskStatus.Running("first", ts)))

        val result = task.withStatus(TaskStatus.Running("second", ts))

        assertEquals(2, result.statusHistory.size)
        assertEquals("second", result.statusHistory.last().message)
    }

    @Test
    fun `withStatus caps history to MAX_STATUS_HISTORY_SIZE entries`() {
        val initial = (0 until JobTask.MAX_STATUS_HISTORY_SIZE).map { TaskStatus.Running("status-$it", ts) }
        val task = createTask(initial)

        val result = task.withStatus(TaskStatus.Running("overflow", ts))

        assertEquals(JobTask.MAX_STATUS_HISTORY_SIZE, result.statusHistory.size)
    }

    @Test
    fun `withStatus retains the most recent entries when capping`() {
        val initial = (0 until JobTask.MAX_STATUS_HISTORY_SIZE).map { TaskStatus.Running("status-$it", ts) }
        val task = createTask(initial)

        val result = task.withStatus(TaskStatus.Running("overflow", ts))

        assertEquals("overflow", result.statusHistory.last().message)
        // The oldest entry should have been dropped.
        assertEquals("status-1", result.statusHistory.first().message)
    }

    @Test
    fun `withStatus does not cap when below the limit`() {
        val task = createTask(listOf(TaskStatus.Running("first", ts)))

        val result = task.withStatus(TaskStatus.Running("second", ts))

        assertEquals(2, result.statusHistory.size)
    }
}

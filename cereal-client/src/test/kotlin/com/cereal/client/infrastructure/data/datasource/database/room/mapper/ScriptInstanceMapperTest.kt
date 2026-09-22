package com.cereal.client.infrastructure.data.datasource.database.room.mapper

import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.infrastructure.data.datasource.database.room.entity.TaskExecutionStatus
import com.cereal.client.infrastructure.data.datasource.database.room.entity.TaskStatusEntity
import com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ScriptInstanceMapperTest {
    private val keyValueMapper = mockk<KeyValueRoomMapper>(relaxed = true)
    private val mapper = ScriptInstanceMapper(keyValueMapper, mockk(relaxed = true), mockk(relaxed = true))

    // ── createFinishedTaskStatusEntities ───────────────────────────────────

    @OptIn(ExperimentalTime::class)
    @Test
    fun `createTaskStatusEntities should persist stackTrace for Error status`() {
        val task = mockk<Task>(relaxed = true)
        val errorStatus =
            TaskStatus.Error(
                message = "Something went wrong",
                stackTrace = "java.lang.RuntimeException: Something went wrong\n\tat Foo.bar(Foo.kt:42)",
                timestamp = Clock.System.now(),
            )
        every { task.statusHistory } returns listOf(errorStatus)

        val entities = mapper.createTaskStatusEntities(task, UUID.randomUUID())

        assertEquals(1, entities.size)
        assertEquals(
            "java.lang.RuntimeException: Something went wrong\n\tat Foo.bar(Foo.kt:42)",
            entities[0].stackTrace?.value,
        )
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `createTaskStatusEntities should persist null stackTrace when Error has no stackTrace`() {
        val task = mockk<Task>(relaxed = true)
        every { task.statusHistory } returns
            listOf(
                TaskStatus.Error(message = "Something went wrong", stackTrace = null, timestamp = Clock.System.now()),
            )

        val entities = mapper.createTaskStatusEntities(task, UUID.randomUUID())

        assertNull(entities[0].stackTrace)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `createTaskStatusEntities should persist null stackTrace for non-Error statuses`() {
        val task = mockk<Task>(relaxed = true)
        every { task.statusHistory } returns
            listOf(
                TaskStatus.Idle(timestamp = Clock.System.now()),
                TaskStatus.Running(message = "Running", timestamp = Clock.System.now()),
                TaskStatus.Success(message = "Done", timestamp = Clock.System.now()),
            )

        val entities = mapper.createTaskStatusEntities(task, UUID.randomUUID())

        entities.forEach { entity ->
            assertNull(entity.stackTrace, "Non-error status should have null stackTrace")
        }
    }

    // ── mapStatusEntity (ERROR read path) ─────────────────────────────────

    @Test
    fun `mapStatusEntity should restore stackTrace for ERROR entity`() {
        val stackTrace = "java.lang.RuntimeException: oops\n\tat Foo.bar(Foo.kt:1)"
        val entity = makeErrorStatusEntity(stackTrace = stackTrace)

        val status = mapper.mapStatusEntity(entity)

        assertEquals(stackTrace, (status as TaskStatus.Error).stackTrace)
    }

    @Test
    fun `mapStatusEntity should restore null stackTrace when stack_trace column is null`() {
        val entity = makeErrorStatusEntity(stackTrace = null)

        val status = mapper.mapStatusEntity(entity)

        assertNull((status as TaskStatus.Error).stackTrace)
    }

    // ── helpers ───────────────────────────────────────────────────────────

    @OptIn(ExperimentalTime::class)
    private fun makeErrorStatusEntity(stackTrace: String?): TaskStatusEntity =
        TaskStatusEntity(
            id = UUID.randomUUID(),
            taskId = UUID.randomUUID(),
            message = EncryptedString.from("error msg"),
            stackTrace = stackTrace?.let { EncryptedString.from(it) },
            timestamp = System.currentTimeMillis(),
            status = TaskExecutionStatus.ERROR,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        )
}

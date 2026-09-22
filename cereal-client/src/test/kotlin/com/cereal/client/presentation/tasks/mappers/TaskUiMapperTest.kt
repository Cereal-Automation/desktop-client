@file:OptIn(ExperimentalTime::class)

package com.cereal.client.presentation.tasks.mappers

import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class TaskUiMapperTest {
    private fun buildTask(
        status: TaskStatus,
    ): Task {
        val scriptPackage =
            mockk<ScriptPackage>(relaxed = true)
        val packageInstance =
            mockk<ScriptPackageInstance> {
                every { definition } returns scriptPackage
            }
        val scriptInstance =
            mockk<ScriptInstance>(relaxed = true) {
                every { this@mockk.packageInstance } returns packageInstance
            }
        return mockk<Task>(relaxed = true) {
            every { this@mockk.status } returns status
            every { this@mockk.statusHistory } returns emptyList()
            every { this@mockk.scriptInstance } returns scriptInstance
            every { configuration } returns emptyMap()
        }
    }

    @Test
    fun `toUiModel should return null stackTrace when status is not TaskStatus Error`() {
        val task = buildTask(status = TaskStatus.Running(message = "In progress", timestamp = Clock.System.now()))

        val uiModel = task.toUiModel(taskNumber = 1)

        assertNull(uiModel.stackTrace)
    }

    @Test
    fun `toUiModel should return stackTrace when status is TaskStatus Error`() {
        val expectedStackTrace = "java.lang.RuntimeException: Something went wrong\n\tat com.cereal.Foo.bar(Foo.kt:42)"
        val task = buildTask(status = TaskStatus.Error(message = "Failed", stackTrace = expectedStackTrace, timestamp = Clock.System.now()))

        val uiModel = task.toUiModel(taskNumber = 1)

        assertEquals(expectedStackTrace, uiModel.stackTrace)
    }

    @Test
    fun `toUiModel should return null stackTrace when status is TaskStatus Error with no stackTrace`() {
        val task = buildTask(status = TaskStatus.Error(message = "Failed", stackTrace = null, timestamp = Clock.System.now()))

        val uiModel = task.toUiModel(taskNumber = 1)

        assertNull(uiModel.stackTrace)
    }

    @Test
    fun `toUiModel isError should be true when status is TaskStatus Error`() {
        val task = buildTask(status = TaskStatus.Error(message = "Failed", stackTrace = null, timestamp = Clock.System.now()))

        val uiModel = task.toUiModel(taskNumber = 1)

        assertTrue(uiModel.isError)
    }

    @Test
    fun `toUiModel isError should be false when status is not TaskStatus Error`() {
        val task = buildTask(status = TaskStatus.Running(message = "In progress", timestamp = Clock.System.now()))

        val uiModel = task.toUiModel(taskNumber = 1)

        assertFalse(uiModel.isError)
    }
}

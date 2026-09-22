package com.cereal.client.application.interactor.task

import com.cereal.client.application.task.TaskManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StopTaskInteractorTest {
    // TaskManager is a concrete orchestration class with heavy collaborators and no observable
    // state of its own; the interactor only delegates stopTask to it.
    private val taskManager = mockk<TaskManager>(relaxed = true)

    @Test
    fun `run should stop the task with the given id`() =
        runTest {
            val interactor = StopTaskInteractor(taskManager)

            interactor.run(StopTaskInteractor.Params("task-1"))

            coVerify { taskManager.stopTask("task-1") }
        }

    @Test
    fun `run should propagate exceptions thrown by the task manager`() =
        runTest {
            val interactor = StopTaskInteractor(taskManager)
            coEvery { taskManager.stopTask("boom") } throws IllegalStateException("failure")

            assertThrows<IllegalStateException> {
                interactor.run(StopTaskInteractor.Params("boom"))
            }
        }
}

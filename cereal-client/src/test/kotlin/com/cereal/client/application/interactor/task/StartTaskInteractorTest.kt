package com.cereal.client.application.interactor.task

import com.cereal.client.application.task.TaskManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StartTaskInteractorTest {
    // TaskManager is a concrete orchestration class with heavy collaborators and no observable
    // state of its own; the interactor only delegates startTask to it. We mock it (relaxed) and
    // assert the delegation happens with the right id, plus failure propagation.
    private val taskManager = mockk<TaskManager>(relaxed = true)

    @Test
    fun `run should start the task with the given id`() =
        runTest {
            val interactor = StartTaskInteractor(taskManager)

            interactor.run(StartTaskInteractor.Params("task-1"))

            coVerify { taskManager.startTask("task-1") }
        }

    @Test
    fun `run should propagate exceptions thrown by the task manager`() =
        runTest {
            val interactor = StartTaskInteractor(taskManager)
            coEvery { taskManager.startTask("missing") } throws IllegalStateException("No task with id missing found.")

            assertThrows<IllegalStateException> {
                interactor.run(StartTaskInteractor.Params("missing"))
            }
        }
}

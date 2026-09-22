package com.cereal.client.application.interactor.task

import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import fixtures.aTask
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UserInteractionDismissedInteractorTest {
    @Test
    fun `run should clear the user interaction on the targeted task`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val interactor = UserInteractionDismissedInteractor(tasksRepository)

            val task = aTask("task-1", "com.example.one", running = true)
            tasksRepository.addTask(task)
            // Seed an existing interaction so we can observe it being cleared.
            tasksRepository.setUserInteraction("task-1", mockk<UserInteraction.ContinueButton>(relaxed = true))

            interactor.run(UserInteractionDismissedInteractor.Params("task-1"))

            assertNull(tasksRepository.getTask("task-1")?.userInteraction)
        }

    @Test
    fun `run should be a no-op for an unknown task id`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val interactor = UserInteractionDismissedInteractor(tasksRepository)

            // Should not throw even though no task with this id exists.
            interactor.run(UserInteractionDismissedInteractor.Params("missing"))

            assertNull(tasksRepository.getTask("missing"))
        }
}

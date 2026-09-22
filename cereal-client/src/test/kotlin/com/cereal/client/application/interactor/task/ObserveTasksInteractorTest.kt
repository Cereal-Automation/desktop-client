package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import fixtures.aTask
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObserveTasksInteractorTest {
    @Test
    fun `run should emit the current tasks from the repository`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val interactor = ObserveTasksInteractor(tasksRepository)
            tasksRepository.addTask(aTask("task-1", "com.example.one", running = false))
            tasksRepository.addTask(aTask("task-2", "com.example.two", running = true))

            val tasks = interactor.run(Interactor.None()).first()

            assertEquals(setOf("task-1", "task-2"), tasks.map { it.id }.toSet())
        }

    @Test
    fun `run should emit an empty list when there are no tasks`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val interactor = ObserveTasksInteractor(tasksRepository)

            val tasks = interactor.run(Interactor.None()).first()

            assertTrue(tasks.isEmpty())
        }
}

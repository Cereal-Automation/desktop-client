package com.cereal.client.application.interactor.marketplace

import com.cereal.client.application.Interactor
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import fixtures.aTask
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetPackagesWithRunningTasksInteractorTest {
    @Test
    fun `emits set of packages with at least one running task`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            tasksRepository.addAllTasks(
                listOf(
                    aTask("t1", "com.a", running = true),
                    aTask("t2", "com.a", running = false),
                    aTask("t3", "com.b", running = false),
                    aTask("t4", "com.c", running = true),
                ),
            )

            val interactor = GetPackagesWithRunningTasksInteractor(tasksRepository)
            val result = interactor.run(Interactor.None()).first()

            assertEquals(setOf("com.a", "com.c"), result)
        }

    @Test
    fun `emits empty set when no tasks are running`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            tasksRepository.addAllTasks(
                listOf(
                    aTask("t1", "com.a", running = false),
                    aTask("t2", "com.b", running = false),
                ),
            )

            val interactor = GetPackagesWithRunningTasksInteractor(tasksRepository)
            val result = interactor.run(Interactor.None()).first()

            assertEquals(emptySet<String>(), result)
        }
}

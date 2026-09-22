package com.cereal.client.application.interactor.marketplace

import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import fixtures.aTask
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HasRunningTasksForScriptInteractorTest {
    private lateinit var tasksRepository: InMemoryTasksRepository
    private lateinit var interactor: HasRunningTasksForScriptInteractor

    @BeforeEach
    fun setUp() {
        tasksRepository = InMemoryTasksRepository()
        interactor = HasRunningTasksForScriptInteractor(tasksRepository)
    }

    @Test
    fun `emits true when any running task belongs to the package`() =
        runTest {
            tasksRepository.addAllTasks(
                listOf(
                    aTask("t1", "com.other", running = true),
                    aTask("t2", "com.example.script", running = true),
                    aTask("t3", "com.example.script", running = false),
                ),
            )

            val result = interactor.run(HasRunningTasksForScriptInteractor.Params("com.example.script")).first()

            assertEquals(true, result)
        }

    @Test
    fun `emits false when no running task belongs to the package`() =
        runTest {
            tasksRepository.addAllTasks(
                listOf(
                    aTask("t1", "com.example.script", running = false),
                    aTask("t2", "com.other", running = true),
                ),
            )

            val result = interactor.run(HasRunningTasksForScriptInteractor.Params("com.example.script")).first()

            assertEquals(false, result)
        }

    @Test
    fun `emits false when there are no tasks at all`() =
        runTest {
            val result = interactor.run(HasRunningTasksForScriptInteractor.Params("com.example.script")).first()

            assertEquals(false, result)
        }
}

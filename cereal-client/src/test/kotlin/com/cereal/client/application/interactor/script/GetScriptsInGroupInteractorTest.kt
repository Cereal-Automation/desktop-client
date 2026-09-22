package com.cereal.client.application.interactor.script

import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import fixtures.aScriptInstance
import fixtures.aScriptPackageInstance
import fixtures.aTask
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GetScriptsInGroupInteractorTest {
    @Test
    fun `run should emit an empty list when the group has no script instances`() =
        runTest {
            val scriptInstanceRepository = InMemoryScriptInstanceRepository()
            val tasksRepository = InMemoryTasksRepository()
            val interactor = GetScriptsInGroupInteractor(scriptInstanceRepository, tasksRepository)

            val result = interactor.run(GetScriptsInGroupInteractor.Params("group-1")).first()

            assertTrue(result.isEmpty())
        }

    @Test
    fun `run should report task counts and running counts per script instance`() =
        runTest {
            val scriptInstanceRepository = InMemoryScriptInstanceRepository()
            val tasksRepository = InMemoryTasksRepository()
            val interactor = GetScriptsInGroupInteractor(scriptInstanceRepository, tasksRepository)

            val packageInstance = aScriptPackageInstance("instance-1", "com.example.one")
            val scriptInstance = aScriptInstance(packageInstance)
            scriptInstanceRepository.seed(listOf(packageInstance))

            // Two tasks for this instance, one running, one idle.
            tasksRepository.addTask(aTask("task-1", scriptInstance, running = true))
            tasksRepository.addTask(aTask("task-2", scriptInstance, running = false))

            val result = interactor.run(GetScriptsInGroupInteractor.Params("group-1")).first()

            assertEquals(1, result.size)
            val entry = result.single()
            assertEquals("instance-1", entry.scriptPackageInstance.id)
            assertEquals(2, entry.taskCount)
            assertEquals(1, entry.runningTaskCount)
        }

    @Test
    fun `run should report zero counts when a script instance has no tasks`() =
        runTest {
            val scriptInstanceRepository = InMemoryScriptInstanceRepository()
            val tasksRepository = InMemoryTasksRepository()
            val interactor = GetScriptsInGroupInteractor(scriptInstanceRepository, tasksRepository)

            val packageInstance = aScriptPackageInstance("instance-1", "com.example.one")
            scriptInstanceRepository.seed(listOf(packageInstance))

            // A task for a different, unrelated instance must not be counted.
            tasksRepository.addTask(aTask("other-task", "com.example.other", running = true))

            val result = interactor.run(GetScriptsInGroupInteractor.Params("group-1")).first()

            assertEquals(1, result.size)
            assertEquals(0, result.single().taskCount)
            assertEquals(0, result.single().runningTaskCount)
        }
}

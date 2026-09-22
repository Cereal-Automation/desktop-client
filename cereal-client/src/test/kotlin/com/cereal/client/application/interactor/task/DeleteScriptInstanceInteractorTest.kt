package com.cereal.client.application.interactor.task

import com.cereal.client.application.script.ScriptInstanceManager
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import fixtures.aScriptInstance
import fixtures.aScriptPackageInstance
import fixtures.aTask
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeleteScriptInstanceInteractorTest {
    // ScriptInstanceManager is real orchestration over the repository fakes; only TaskManager —
    // a heavy collaborator with no observable state — is mocked (relaxed), following the
    // DeleteTaskGroupInteractorTest convention. We assert on the observable repository state.
    private val taskManager = mockk<TaskManager>(relaxed = true)

    private fun manager(
        tasksRepository: InMemoryTasksRepository,
        scriptInstanceRepository: InMemoryScriptInstanceRepository,
    ) = ScriptInstanceManager(taskManager, tasksRepository, scriptInstanceRepository)

    @Test
    fun `run should remove the script package instance from the repository`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val scriptInstanceRepository = InMemoryScriptInstanceRepository()
            val interactor = DeleteScriptInstanceInteractor(manager(tasksRepository, scriptInstanceRepository))

            val target = aScriptPackageInstance("instance-1", "com.example.one")
            val other = aScriptPackageInstance("instance-2", "com.example.two")
            scriptInstanceRepository.seed(listOf(target, other))

            interactor.run(DeleteScriptInstanceInteractor.Params(target))

            val remaining = scriptInstanceRepository.getScriptPackageInstances().map { it.id }
            assertFalse(remaining.contains("instance-1"))
            assertTrue(remaining.contains("instance-2"))
        }

    @Test
    fun `run should delete each task belonging to the script instance via the task manager`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val scriptInstanceRepository = InMemoryScriptInstanceRepository()
            val interactor = DeleteScriptInstanceInteractor(manager(tasksRepository, scriptInstanceRepository))

            val target = aScriptPackageInstance("instance-1", "com.example.one")
            scriptInstanceRepository.seed(listOf(target))
            val scriptInstance = aScriptInstance(target)
            val task = aTask("task-1", scriptInstance, running = true)
            tasksRepository.addTask(task)

            interactor.run(DeleteScriptInstanceInteractor.Params(target))

            coVerify { taskManager.deleteTask(task) }
            assertTrue(scriptInstanceRepository.getScriptPackageInstances().isEmpty())
        }

    @Test
    fun `run should remove an instance that has no tasks`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val scriptInstanceRepository = InMemoryScriptInstanceRepository()
            val interactor = DeleteScriptInstanceInteractor(manager(tasksRepository, scriptInstanceRepository))

            val target: ScriptPackageInstance = aScriptPackageInstance("instance-1", "com.example.one")
            scriptInstanceRepository.seed(listOf(target))

            interactor.run(DeleteScriptInstanceInteractor.Params(target))

            assertTrue(scriptInstanceRepository.getScriptPackageInstances().isEmpty())
        }
}

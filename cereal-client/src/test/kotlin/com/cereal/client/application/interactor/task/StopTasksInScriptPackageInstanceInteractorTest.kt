package com.cereal.client.application.interactor.task

import com.cereal.client.application.task.TaskManager
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import fixtures.aScriptInstance
import fixtures.aScriptPackageInstance
import fixtures.aTask
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class StopTasksInScriptPackageInstanceInteractorTest {
    // TaskManager has no observable state; we mock it (relaxed) and assert which task ids get stopped.
    private val taskManager = mockk<TaskManager>(relaxed = true)

    @Test
    fun `run should stop only the running tasks of the package instance`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val interactor = StopTasksInScriptPackageInstanceInteractor(taskManager, tasksRepository)

            val packageInstance = aScriptPackageInstance("instance-1", "com.example.one")
            val scriptInstance = aScriptInstance(packageInstance)
            tasksRepository.addTask(aTask("running-1", scriptInstance, running = true))
            tasksRepository.addTask(aTask("running-2", scriptInstance, running = true))
            tasksRepository.addTask(aTask("idle-1", scriptInstance, running = false))

            interactor.run(StopTasksInScriptPackageInstanceInteractor.Params(packageInstance))

            coVerify(exactly = 1) { taskManager.stopTask("running-1") }
            coVerify(exactly = 1) { taskManager.stopTask("running-2") }
            coVerify(exactly = 0) { taskManager.stopTask("idle-1") }
        }

    @Test
    fun `run should not stop tasks belonging to a different package instance`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val interactor = StopTasksInScriptPackageInstanceInteractor(taskManager, tasksRepository)

            val target = aScriptPackageInstance("instance-1", "com.example.one")
            val other = aScriptPackageInstance("instance-2", "com.example.two")
            tasksRepository.addTask(aTask("target-running", aScriptInstance(target), running = true))
            tasksRepository.addTask(aTask("other-running", aScriptInstance(other), running = true))

            interactor.run(StopTasksInScriptPackageInstanceInteractor.Params(target))

            coVerify(exactly = 1) { taskManager.stopTask("target-running") }
            coVerify(exactly = 0) { taskManager.stopTask("other-running") }
        }

    @Test
    fun `run should do nothing when the package instance has no tasks`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val interactor = StopTasksInScriptPackageInstanceInteractor(taskManager, tasksRepository)
            val packageInstance = aScriptPackageInstance("instance-1", "com.example.one")

            interactor.run(StopTasksInScriptPackageInstanceInteractor.Params(packageInstance))

            coVerify(exactly = 0) { taskManager.stopTask(any()) }
        }
}

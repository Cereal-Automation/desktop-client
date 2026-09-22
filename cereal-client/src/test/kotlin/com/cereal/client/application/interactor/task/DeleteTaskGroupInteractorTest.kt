package com.cereal.client.application.interactor.task

import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import fixtures.aScriptPackageInstance
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeleteTaskGroupInteractorTest {
    // TaskManager is a concrete orchestration class with heavy collaborators; the interactor only
    // delegates task-stopping to it. We keep it mocked (relaxed) — there is no group with running
    // tasks in these scenarios, so it is never invoked.
    private val taskManager = mockk<TaskManager>(relaxed = true)

    @Test
    fun `run should delete task group and all related script instances`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val scriptInstanceRepository = InMemoryScriptInstanceRepository()
            val interactor = DeleteTaskGroupInteractor(tasksRepository, taskManager, scriptInstanceRepository)

            val group = ScriptPackageGroup("group-1", "TestGroup")
            tasksRepository.createScriptInstanceGroup(group)
            scriptInstanceRepository.seed(
                listOf(
                    aScriptPackageInstance("instance-1", "com.example.one"),
                    aScriptPackageInstance("instance-2", "com.example.two"),
                ),
            )

            interactor.run(DeleteTaskGroupInteractor.Params(group))

            assertTrue(tasksRepository.getTaskGroups().first().isEmpty())
            assertTrue(scriptInstanceRepository.getScriptPackageInstances().isEmpty())
        }

    @Test
    fun `run should handle empty group with no script instances`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val scriptInstanceRepository = InMemoryScriptInstanceRepository()
            val interactor = DeleteTaskGroupInteractor(tasksRepository, taskManager, scriptInstanceRepository)

            val group = ScriptPackageGroup("group-1", "EmptyGroup")
            val otherGroup = ScriptPackageGroup("group-2", "KeepMe")
            tasksRepository.createScriptInstanceGroup(group)
            tasksRepository.createScriptInstanceGroup(otherGroup)

            interactor.run(DeleteTaskGroupInteractor.Params(group))

            val remaining = tasksRepository.getTaskGroups().first()
            assertEquals(listOf(otherGroup), remaining)
        }
}

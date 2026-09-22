package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetOrCreateDefaultTaskGroupInteractorTest {
    @Test
    fun `run should create a Default group when none exists`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val interactor = GetOrCreateDefaultTaskGroupInteractor(tasksRepository)

            val result = interactor.run(Interactor.None())

            assertEquals(ScriptPackageGroup.DEFAULT_GROUP_NAME, result.name)
            val groups = tasksRepository.getTaskGroups().first()
            assertEquals(listOf(result), groups)
        }

    @Test
    fun `run should return the existing Default group without creating a new one`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val interactor = GetOrCreateDefaultTaskGroupInteractor(tasksRepository)
            val existing = ScriptPackageGroup("default-id", ScriptPackageGroup.DEFAULT_GROUP_NAME)
            tasksRepository.createScriptInstanceGroup(existing)

            val result = interactor.run(Interactor.None())

            assertEquals(existing, result)
            assertEquals(1, tasksRepository.getTaskGroups().first().size)
        }

    @Test
    fun `run should create a Default group when only non-default groups exist`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val interactor = GetOrCreateDefaultTaskGroupInteractor(tasksRepository)
            tasksRepository.createScriptInstanceGroup(ScriptPackageGroup("group-1", "Other"))

            val result = interactor.run(Interactor.None())

            assertEquals(ScriptPackageGroup.DEFAULT_GROUP_NAME, result.name)
            val names = tasksRepository.getTaskGroups().first().map { it.name }
            assertEquals(setOf("Other", ScriptPackageGroup.DEFAULT_GROUP_NAME), names.toSet())
        }
}

package com.cereal.client.application.interactor.task

import com.cereal.client.domain.model.exception.InvalidScriptPackageGroupException
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CreateScriptInstanceGroupInteractorTest {
    @Test
    fun `run should create a group with the given name and a generated id`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val interactor = CreateScriptInstanceGroupInteractor(tasksRepository)

            val created = interactor.run(CreateScriptInstanceGroupInteractor.Params("My Group"))

            assertEquals("My Group", created.name)
            assertNotNull(created.id)
            assertTrue(created.id.isNotBlank())

            val groups = tasksRepository.getTaskGroups().first()
            assertEquals(listOf(created), groups)
        }

    @Test
    fun `run should create groups with distinct ids on repeated calls`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val interactor = CreateScriptInstanceGroupInteractor(tasksRepository)

            val first = interactor.run(CreateScriptInstanceGroupInteractor.Params("Alpha"))
            val second = interactor.run(CreateScriptInstanceGroupInteractor.Params("Beta"))

            assertEquals(2, tasksRepository.getTaskGroups().first().size)
            assertTrue(first.id != second.id)
        }

    @Test
    fun `run should reject a blank group name before touching the repository`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val interactor = CreateScriptInstanceGroupInteractor(tasksRepository)

            assertThrows<InvalidScriptPackageGroupException> {
                interactor.run(CreateScriptInstanceGroupInteractor.Params(" "))
            }

            assertTrue(tasksRepository.getTaskGroups().first().isEmpty())
        }
}

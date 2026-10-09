package com.cereal.client.application.interactor.task

import com.cereal.client.domain.model.exception.InvalidScriptPackageGroupException
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EditScriptInstanceGroupInteractorTest {
    @Test
    fun `run should rename an existing group keeping its id`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            tasksRepository.createScriptInstanceGroup(ScriptPackageGroup("group-1", "Original"))
            val interactor = EditScriptInstanceGroupInteractor(tasksRepository)

            interactor.run(EditScriptInstanceGroupInteractor.Params(groupId = "group-1", name = "New Name"))

            // ScriptPackageGroup equality is id-only, so this only passes if the rename is re-emitted.
            val group = tasksRepository.getTaskGroups().first().single()
            assertEquals("group-1", group.id)
            assertEquals("New Name", group.name)
        }

    @Test
    fun `run should forward the targeted group id and new name`() =
        runTest {
            val tasksRepository = mockk<TasksRepository>(relaxed = true)
            val captured = slot<ScriptPackageGroup>()
            coEvery { tasksRepository.updateScriptInstanceGroup(capture(captured)) } returns Unit
            val interactor = EditScriptInstanceGroupInteractor(tasksRepository)

            interactor.run(EditScriptInstanceGroupInteractor.Params(groupId = "group-2", name = "Renamed"))

            assertEquals("group-2", captured.captured.id)
            assertEquals("Renamed", captured.captured.name)
        }

    @Test
    fun `run should reject an invalid name before touching the repository`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val interactor = EditScriptInstanceGroupInteractor(tasksRepository)
            tasksRepository.createScriptInstanceGroup(ScriptPackageGroup("group-1", "Original"))

            assertThrows<InvalidScriptPackageGroupException> {
                interactor.run(EditScriptInstanceGroupInteractor.Params(groupId = "group-1", name = "bad/name!"))
            }

            assertEquals(
                "Original",
                tasksRepository
                    .getTaskGroups()
                    .first()
                    .first()
                    .name,
            )
        }
}

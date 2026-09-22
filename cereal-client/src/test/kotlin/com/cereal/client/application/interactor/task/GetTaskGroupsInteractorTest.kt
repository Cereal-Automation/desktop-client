package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class GetTaskGroupsInteractorTest {
    @Test
    fun `should emit empty list and not create any group when no groups exist`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val interactor = GetTaskGroupsInteractor(tasksRepository)

            val groups = interactor.run(Interactor.None()).first()

            // Observing the groups must be a pure read: an empty store stays empty.
            assertEquals(emptyList<ScriptPackageGroup>(), groups)
            assertEquals(emptyList<ScriptPackageGroup>(), tasksRepository.getTaskGroups().first())
        }

    @Test
    fun `should return groups sorted by name case-insensitive`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val interactor = GetTaskGroupsInteractor(tasksRepository)

            val groupB = ScriptPackageGroup(UUID.randomUUID().toString(), "B Group")
            val groupa = ScriptPackageGroup(UUID.randomUUID().toString(), "a Group")
            val groupC = ScriptPackageGroup(UUID.randomUUID().toString(), "C Group")

            tasksRepository.createScriptInstanceGroup(groupB)
            tasksRepository.createScriptInstanceGroup(groupa)
            tasksRepository.createScriptInstanceGroup(groupC)

            val groups = interactor.run(Interactor.None()).first()

            assertEquals(3, groups.size)
            assertEquals("a Group", groups[0].name)
            assertEquals("B Group", groups[1].name)
            assertEquals("C Group", groups[2].name)
        }
}

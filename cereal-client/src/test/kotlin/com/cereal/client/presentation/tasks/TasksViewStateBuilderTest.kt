package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.task.UserInteraction
import fixtures.aScriptInstance
import fixtures.aScriptPackageInstance
import fixtures.aTask
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TasksViewStateBuilderTest {
    @Test
    fun `returns NoSelection when no script package instance is selected`() {
        val state = TasksViewStateBuilder.buildDetailViewState(tasks = null, selectedScriptPackageInstance = null)

        assertTrue(state is TaskViewState.DetailViewState.NoSelection)
    }

    @Test
    fun `returns Empty when the selected package has no tasks`() {
        val packageInstance = aScriptPackageInstance("pkg1", "com.test")

        val state =
            TasksViewStateBuilder.buildDetailViewState(
                tasks = emptyList(),
                selectedScriptPackageInstance = packageInstance,
            )

        assertTrue(state is TaskViewState.DetailViewState.Empty)
        assertEquals("Test", state.headerTitle)
    }

    @Test
    fun `returns Filled with per-status counts for matching tasks`() {
        val packageInstance = aScriptPackageInstance("pkg1", "com.test")
        val scriptInstance = aScriptInstance(packageInstance)
        val running = aTask("t1", scriptInstance, running = true)
        val idle = aTask("t2", scriptInstance, running = false)

        val state =
            TasksViewStateBuilder.buildDetailViewState(
                tasks = listOf(running, idle),
                selectedScriptPackageInstance = packageInstance,
            ) as TaskViewState.DetailViewState.Filled

        assertEquals(1, state.scriptInstances.size)
        assertEquals(
            2,
            state.scriptInstances
                .first()
                .tasks.size,
        )
        assertEquals(1, state.runningTasksCount)
        assertEquals(1, state.idleTasksCount)
        assertEquals(0, state.finishedTasksCount)
        assertEquals(0, state.erroredTasksCount)
        assertTrue(state.startAllTasksEnabled)
        assertTrue(state.stopAllTasksEnabled)
    }

    @Test
    fun `ignores tasks that belong to a different package`() {
        val selected = aScriptPackageInstance("pkg1", "com.test")
        val otherInstance = aScriptInstance(aScriptPackageInstance("pkg2", "com.other"))
        val foreignTask = aTask("t1", otherInstance, running = true)

        val state =
            TasksViewStateBuilder.buildDetailViewState(
                tasks = listOf(foreignTask),
                selectedScriptPackageInstance = selected,
            )

        assertTrue(state is TaskViewState.DetailViewState.Empty)
    }

    @Test
    fun `extractUserInteractions is empty when no task awaits interaction`() {
        val scriptInstance = aScriptInstance(aScriptPackageInstance("pkg1", "com.test"))

        assertTrue(TasksViewStateBuilder.extractUserInteractions(listOf(aTask("t1", scriptInstance, running = true))).isEmpty())
    }

    @Test
    fun `extractUserInteractions is empty when there are no tasks`() {
        assertFalse(TasksViewStateBuilder.extractUserInteractions(null).isNotEmpty())
    }

    @Test
    fun `extractUserInteractions includes tasks of every package, not just the selected one`() {
        val interaction = UserInteraction.ContinueButton(mockk(relaxed = true))
        val first = aTask("t1", aScriptInstance(aScriptPackageInstance("pkg1", "com.one")), running = true)
        val second = aTask("t2", aScriptInstance(aScriptPackageInstance("pkg2", "com.two")), running = true)

        val result =
            TasksViewStateBuilder.extractUserInteractions(
                listOf(first.copy(userInteraction = interaction), second.copy(userInteraction = interaction)),
            )

        assertEquals(listOf("t1", "t2"), result.map { it.id.id })
    }
}

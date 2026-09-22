package com.cereal.client.presentation.tasks

import fixtures.aScriptInstance
import fixtures.aScriptPackageInstance
import fixtures.aTask
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
        val packageInstance = aScriptPackageInstance("pkg1", "com.test")
        val scriptInstance = aScriptInstance(packageInstance)
        val filled =
            TasksViewStateBuilder.buildDetailViewState(
                tasks = listOf(aTask("t1", scriptInstance, running = true)),
                selectedScriptPackageInstance = packageInstance,
            )

        assertTrue(TasksViewStateBuilder.extractUserInteractions(filled).isEmpty())
    }

    @Test
    fun `extractUserInteractions is empty for a non-filled state`() {
        val noSelection = TaskViewState.DetailViewState.NoSelection

        assertFalse(TasksViewStateBuilder.extractUserInteractions(noSelection).isNotEmpty())
    }
}

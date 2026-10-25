package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.UserInteraction
import fixtures.aScriptInstance
import fixtures.aScriptPackageInstance
import fixtures.aTask
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class TasksViewStateBuilderTest {
    private val default = ScriptPackageGroup("default", "Default")
    private val work = ScriptPackageGroup("work", "Work")

    @Test
    fun `is Loading until the groups and their setups have loaded`() {
        assertEquals(TaskViewState.ListState.Loading, TasksViewStateBuilder.buildListState(null, emptyList()))
    }

    @Test
    fun `is Empty with no groups, or only one group without setups`() {
        assertEquals(TaskViewState.ListState.Empty, TasksViewStateBuilder.buildListState(emptyList(), emptyList()))
        assertEquals(TaskViewState.ListState.Empty, TasksViewStateBuilder.buildListState(listOf(default to emptyList()), emptyList()))
    }

    @Test
    fun `lists groups the user made even while they hold no setups`() {
        val state = TasksViewStateBuilder.buildListState(listOf(default to emptyList(), work to emptyList()), emptyList())

        assertEquals(listOf("Default", "Work"), assertIs<TaskViewState.ListState.Filled>(state).groups.map { it.group.name })
    }

    @Test
    fun `numbers a setup's tasks by creation and lists running ones first`() {
        val setup = aScriptPackageInstance("pkg1", "com.test")
        val script = aScriptInstance(setup)
        val now = Clock.System.now()
        val first = aTask("t1", script, running = false).copy(createdAt = now)
        val second = aTask("t2", script, running = true).copy(createdAt = now + 1.seconds)

        val state = TasksViewStateBuilder.buildListState(listOf(default to listOf(setup)), listOf(first, second))

        val rows =
            assertIs<TaskViewState.ListState.Filled>(state)
                .groups
                .single()
                .setups
                .single()
                .scripts
                .flatMap { it.tasks }
        assertEquals(listOf("t2" to 2, "t1" to 1), rows.map { it.id.id to it.taskNumber })
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

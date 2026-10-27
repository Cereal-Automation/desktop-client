package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.TaskSetupSort
import com.cereal.client.domain.model.task.UserInteraction
import fixtures.aScriptInstance
import fixtures.aScriptPackageInstance
import fixtures.aScriptPackageWithVariant
import fixtures.aTask
import fixtures.withIdentifier
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

        assertEquals(listOf("Default", "Work"), assertIs<TaskViewState.ListState.Filled>(state).groups.map { it.name })
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
    fun `names a variant setup after its variant and a default-entry setup after its package`() {
        val variant =
            aScriptPackageInstance(
                id = "v1",
                packageName = "com.example",
                definition = aScriptPackageWithVariant("com.example", variantId = "checkout", variantName = "Checkout"),
                variantId = "checkout",
            )
        val defaultEntry = aScriptPackageInstance("d1", "com.example")

        val state = TasksViewStateBuilder.buildListState(listOf(default to listOf(variant, defaultEntry)), emptyList(), sort = TaskSetupSort.OLDEST_FIRST)

        val names =
            assertIs<TaskViewState.ListState.Filled>(state)
                .groups
                .single()
                .setups
                .map { it.name }
        assertEquals(listOf("Checkout", "Test"), names)
    }

    @Test
    fun `titles a setup with its identifier over its script name, and with its script name when it has none`() {
        val identified = aScriptPackageInstance("i1", "com.example").withIdentifier("Find cheap flights")
        val blank = aScriptPackageInstance("b1", "com.example").withIdentifier(" ")
        val plain = aScriptPackageInstance("p1", "com.example")

        val state = TasksViewStateBuilder.buildListState(listOf(default to listOf(identified, blank, plain)), emptyList(), sort = TaskSetupSort.OLDEST_FIRST)

        val setups = assertIs<TaskViewState.ListState.Filled>(state).groups.single().setups
        assertEquals(
            listOf(
                Triple("Find cheap flights", "Test", "Test"),
                Triple("Test", null, "Test"),
                Triple("Test", null, "Test"),
            ),
            setups.map { Triple(it.name, it.subtitle, it.scriptName) },
        )
    }

    @Test
    fun `a setup with one task carries it, while one with more stays a card even when a filter leaves one`() {
        val single = aScriptPackageInstance("pkg1", "com.one")
        val multi = aScriptPackageInstance("pkg2", "com.two")
        val tasks =
            listOf(
                aTask("s1", aScriptInstance(single), running = true),
                aTask("m1", aScriptInstance(multi), running = true),
                aTask("m2", aScriptInstance(multi), running = false),
            )

        val setups =
            assertIs<TaskViewState.ListState.Filled>(
                TasksViewStateBuilder.buildListState(listOf(default to listOf(single, multi)), tasks, statusFilter = TaskFilter.RUNNING),
            ).groups
                .single()
                .setups
                .associateBy { it.setupId }

        assertEquals(
            "s1",
            setups
                .getValue("pkg1")
                .singleTask
                ?.id
                ?.id,
        )
        assertEquals(null, setups.getValue("pkg2").singleTask)
        assertEquals(
            listOf("m1"),
            setups
                .getValue("pkg2")
                .scripts
                .flatMap { it.tasks }
                .map { it.id.id },
        )
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

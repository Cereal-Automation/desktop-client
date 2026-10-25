package com.cereal.client.presentation.tasks

import fixtures.TasksScreenWorld
import fixtures.errored
import fixtures.idle
import fixtures.running
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

/** Collapsing and expanding task setups in the list, on the real graph (see [TasksScreenWorld]). */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskSetupCollapseViewModelTest {
    private val world = TasksScreenWorld(StandardTestDispatcher())
    private var viewModel: TasksViewModel? = null

    private fun start(): TasksViewModel =
        world.viewModel().also {
            viewModel = it
            settle()
        }

    private fun settle() = world.dispatcher.scheduler.advanceUntilIdle()

    @AfterEach
    fun tearDown() {
        viewModel?.dispose()
    }

    /** Setup id → the task ids it shows (none while collapsed), in list order. */
    private fun TasksViewModel.shown(): Map<String, List<String>> =
        assertIs<TaskViewState.ListState.Filled>(listState.value).groups.flatMap { it.setups }.associate { setup ->
            setup.setupId to if (setup.expanded) setup.scripts.flatMap { it.tasks }.map { it.id.id } else emptyList()
        }

    @Test
    fun `collapsing a setup hides its tasks, and it stays collapsed while its tasks update`() =
        runTest {
            val a = world.setup("a", null, idle(), idle())
            world.setup("b", null, idle())
            val viewModel = start()

            viewModel.onSetupExpandToggled(a.id)

            assertEquals(mapOf("a" to emptyList<String>(), "b" to listOf("b-task-1")), viewModel.shown())

            world.tasksRepository.addStatusHistory("a-task-1", running())
            settle()

            assertEquals(mapOf("a" to emptyList<String>(), "b" to listOf("b-task-1")), viewModel.shown())

            viewModel.onSetupExpandToggled(a.id)

            assertEquals(mapOf("a" to listOf("a-task-1", "a-task-2"), "b" to listOf("b-task-1")), viewModel.shown())
        }

    @Test
    fun `every setup is expanded when the screen opens again`() =
        runTest {
            val a = world.setup("a", null, idle())
            start().apply {
                onSetupExpandToggled(a.id)
                dispose()
            }

            val viewModel = start()

            assertEquals(mapOf("a" to listOf("a-task-1")), viewModel.shown())
        }

    @Test
    fun `a chip other than All forces matching setups open, and All restores the collapse state`() =
        runTest {
            val a = world.setup("a", null, errored(), idle())
            val b = world.setup("b", null, errored())
            world.setup("c", null, idle())
            val viewModel = start()
            viewModel.onSetupExpandToggled(a.id)
            viewModel.onSetupExpandToggled(b.id)

            viewModel.onStatusFilterSelected(TaskFilter.ERROR)

            assertEquals(mapOf("a" to listOf("a-task-1"), "b" to listOf("b-task-1")), viewModel.shown())

            viewModel.onStatusFilterSelected(TaskFilter.ALL)

            assertEquals(mapOf("a" to emptyList(), "b" to emptyList(), "c" to listOf("c-task-1")), viewModel.shown())
        }

    @Test
    fun `focusing a task in a collapsed setup expands it and selects the task`() =
        runTest {
            val a = world.setup("a", null, idle(), errored())
            world.setup("b", null, idle())
            val viewModel = start()
            viewModel.onSetupExpandToggled(a.id)

            viewModel.focusTask("a-task-2")
            settle()

            assertEquals("a-task-2", viewModel.selectedTask.value?.id)
            assertEquals("a-task-2", viewModel.scrollToTaskId.value)
            assertEquals(mapOf("a" to listOf("a-task-1", "a-task-2"), "b" to listOf("b-task-1")), viewModel.shown())
        }

    @Test
    fun `focusing a task hidden by the status chip or the group filter clears those filters`() =
        runTest {
            val work = world.group("Work")
            val a = world.setup("a", null, errored())
            world.setup("b", work, running())
            val viewModel = start()
            viewModel.onSetupExpandToggled(a.id)
            viewModel.onGroupFilterSelected(work.id)
            viewModel.onStatusFilterSelected(TaskFilter.RUNNING)

            viewModel.focusTask("a-task-1")
            settle()

            val list = assertIs<TaskViewState.ListState.Filled>(viewModel.listState.value)
            assertEquals(TaskFilter.ALL, list.chips.single { it.active }.filter)
            assertEquals(null, list.groupFilter?.selectedGroupId)
            assertEquals(mapOf("a" to listOf("a-task-1"), "b" to listOf("b-task-1")), viewModel.shown())
            assertEquals("a-task-1", viewModel.selectedTask.value?.id)
        }
}

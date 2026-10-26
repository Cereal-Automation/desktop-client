package com.cereal.client.presentation.tasks

import fixtures.TasksScreenWorld
import fixtures.errored
import fixtures.idle
import fixtures.running
import fixtures.succeeded
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

/** The status chips and the group filter across every task setup, on the real graph (see [TasksScreenWorld]). */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskFiltersViewModelTest {
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

    private fun TasksViewModel.list(): TaskViewState.ListState.Filled = assertIs<TaskViewState.ListState.Filled>(listState.value)

    /** Group name → setup id → task ids, in list order. */
    private fun TasksViewModel.outline(): Map<String, Map<String, List<String>>> =
        list().groups.associate { group ->
            group.name to group.setups.associate { setup -> setup.setupId to setup.scripts.flatMap { it.tasks }.map { it.id.id } }
        }

    @Test
    fun `the Failed chip lists only failed tasks from every setup and hides setups without failures`() =
        runTest {
            world.setup("a", null, errored(), idle())
            world.setup("b", null, running(), errored())
            world.setup("c", null, idle(), succeeded())
            val viewModel = start()

            viewModel.onStatusFilterSelected(TaskFilter.ERROR)

            assertEquals(mapOf("Default" to mapOf("a" to listOf("a-task-1"), "b" to listOf("b-task-2"))), viewModel.outline())
        }

    /** The shown chips and their counts, in order. */
    private fun TasksViewModel.chips(): List<Pair<TaskFilter, Int>> = list().chips.map { it.filter to it.count }

    @Test
    fun `chip counts are totals across every setup, ignore the active chip and update live`() =
        runTest {
            world.setup("a", null, running(), errored(), idle())
            world.setup("b", null, errored(), succeeded())
            val viewModel = start()
            viewModel.onStatusFilterSelected(TaskFilter.ERROR)

            assertEquals(
                listOf(TaskFilter.ALL to 5, TaskFilter.RUNNING to 1, TaskFilter.ERROR to 2, TaskFilter.SUCCESS to 1, TaskFilter.IDLE to 1),
                viewModel.chips(),
            )

            world.tasksRepository.addStatusHistory("a-task-3", errored())
            settle()

            assertEquals(listOf(TaskFilter.ALL to 5, TaskFilter.RUNNING to 1, TaskFilter.ERROR to 3, TaskFilter.SUCCESS to 1), viewModel.chips())
        }

    @Test
    fun `a chip with no tasks is hidden unless it is All or the active chip`() =
        runTest {
            world.setup("a", null, running(), idle())
            val viewModel = start()

            assertEquals(listOf(TaskFilter.ALL to 2, TaskFilter.RUNNING to 1, TaskFilter.IDLE to 1), viewModel.chips())

            viewModel.onStatusFilterSelected(TaskFilter.SUCCESS)

            assertEquals(listOf(TaskFilter.ALL to 2, TaskFilter.RUNNING to 1, TaskFilter.SUCCESS to 0, TaskFilter.IDLE to 1), viewModel.chips())
            assertEquals(emptyMap<String, Any>(), viewModel.outline())
        }

    /** The group filter's options by name and the selected one ("All groups" is null); null when not offered. */
    private fun TasksViewModel.groupFilter(): Pair<List<String>, String?>? = list().groupFilter?.let { filter -> filter.groups.map { it.name } to filter.groups.firstOrNull { it.id == filter.selectedGroupId }?.name }

    @Test
    fun `the group filter is offered only with more than one group`() =
        runTest {
            world.setup("a", null, idle())
            val viewModel = start()

            assertEquals(null, viewModel.groupFilter())

            world.setup("b", world.group("Work"), idle())
            settle()

            assertEquals(listOf("Default", "Work") to null, viewModel.groupFilter())
        }

    @Test
    fun `picking a group narrows both the sections and the chip counts to that group`() =
        runTest {
            val work = world.group("Work")
            world.setup("a", null, running(), errored())
            world.setup("b", work, errored(), idle())
            world.setup("c", work, succeeded())
            val viewModel = start()

            viewModel.onGroupFilterSelected(work.id)

            assertEquals(mapOf("Work" to mapOf("b" to listOf("b-task-2", "b-task-1"), "c" to listOf("c-task-1"))), viewModel.outline())
            assertEquals(listOf(TaskFilter.ALL to 3, TaskFilter.ERROR to 1, TaskFilter.SUCCESS to 1, TaskFilter.IDLE to 1), viewModel.chips())
            assertEquals(listOf("Default", "Work") to "Work", viewModel.groupFilter())

            viewModel.onStatusFilterSelected(TaskFilter.ERROR)

            assertEquals(mapOf("Work" to mapOf("b" to listOf("b-task-1"))), viewModel.outline())
        }

    @Test
    fun `deleting the picked group shows all groups again`() =
        runTest {
            val work = world.group("Work")
            val drops = world.group("Drops")
            world.setup("a", null, idle())
            world.setup("b", work, idle())
            val viewModel = start()
            viewModel.onGroupFilterSelected(drops.id)

            viewModel.onGroupMenuOptionSelected(drops.id, GroupMenuOption.DELETE)
            settle()

            assertEquals(listOf("Default", "Work") to null, viewModel.groupFilter())
            assertEquals(setOf("Default", "Work"), viewModel.outline().keys)
        }
}

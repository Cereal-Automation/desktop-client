package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.script.ScriptPackageInstance
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Status chips, the Start all / Stop all toggle and Restart errored on [TasksViewModel] (see [TasksScreenWorld]). */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskStatusControlsViewModelTest {
    private val world = TasksScreenWorld(StandardTestDispatcher())
    private var viewModel: TasksViewModel? = null

    @AfterEach
    fun tearDown() {
        viewModel?.dispose()
    }

    private fun open(setup: ScriptPackageInstance): TasksViewModel =
        world.viewModel().also {
            viewModel = it
            world.dispatcher.scheduler.advanceUntilIdle()
            it.hierarchicalListViewModel.selectChild(setup)
            world.dispatcher.scheduler.advanceUntilIdle()
        }

    @Test
    fun `status chips count the selected setup's tasks per status`() =
        runTest {
            val setup = world.setup("setup-1", null, idle(), running(), succeeded(), errored(), errored())

            val controls = assertNotNull(open(setup).taskControls.value)

            assertEquals(TaskCounts(idle = 1, running = 1, finished = 1, errored = 2), controls.counts)
        }

    @Test
    fun `clicking a status chip filters the task list, clicking it again clears the filter`() =
        runTest {
            val setup = world.setup("setup-1", null, idle(), errored(), errored())
            val viewModel = open(setup)

            viewModel.onStatusChipClicked(TaskFilter.ERROR)

            assertEquals(TaskFilter.ERROR, viewModel.taskControls.value?.filter)
            assertEquals(listOf("setup-1-task-2", "setup-1-task-3"), viewModel.visibleTaskIds())

            viewModel.onStatusChipClicked(TaskFilter.ERROR)

            assertEquals(TaskFilter.ALL, viewModel.taskControls.value?.filter)
            assertEquals(listOf("setup-1-task-1", "setup-1-task-2", "setup-1-task-3"), viewModel.visibleTaskIds().sorted())
        }

    @Test
    fun `selecting another setup clears the status filter`() =
        runTest {
            val first = world.setup("setup-1", null, idle(), errored())
            val second = world.setup("setup-2", null, idle())
            val viewModel = open(first)
            viewModel.onStatusChipClicked(TaskFilter.ERROR)

            viewModel.hierarchicalListViewModel.selectChild(second)
            world.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(TaskFilter.ALL, viewModel.taskControls.value?.filter)
            assertEquals(listOf("setup-2-task-1"), viewModel.visibleTaskIds())
        }

    @Test
    fun `clicking another chip switches the filter`() =
        runTest {
            val setup = world.setup("setup-1", null, idle(), errored())
            val viewModel = open(setup)

            viewModel.onStatusChipClicked(TaskFilter.ERROR)
            viewModel.onStatusChipClicked(TaskFilter.IDLE)

            assertEquals(listOf("setup-1-task-1"), viewModel.visibleTaskIds())
        }

    @Test
    fun `toggle offers Stop all while any task runs`() =
        runTest {
            val setup = world.setup("setup-1", null, idle(), running())

            val controls = assertNotNull(open(setup).taskControls.value)

            assertTrue(controls.showStopAll)
        }

    @Test
    fun `toggle offers Start all when nothing runs`() =
        runTest {
            val setup = world.setup("setup-1", null, idle(), succeeded())

            val controls = assertNotNull(open(setup).taskControls.value)

            assertFalse(controls.showStopAll)
            assertTrue(controls.showStartAll)
        }

    @Test
    fun `Start all is hidden when no task can start`() =
        runTest {
            val setup = world.setup("setup-1", null, succeeded(), errored())

            val controls = assertNotNull(open(setup).taskControls.value)

            assertFalse(controls.showStopAll)
            assertFalse(controls.showStartAll)
        }

    @Test
    fun `restart errored is shown only when a task errored`() =
        runTest {
            val clean = world.setup("setup-1", null, idle(), succeeded())
            val failing = world.setup("setup-2", null, idle(), errored())
            val viewModel = open(clean)

            assertFalse(assertNotNull(viewModel.taskControls.value).showRestartErrored)

            viewModel.hierarchicalListViewModel.selectChild(failing)
            world.dispatcher.scheduler.advanceUntilIdle()

            assertTrue(assertNotNull(viewModel.taskControls.value).showRestartErrored)
        }

    private fun TasksViewModel.visibleTaskIds() = visibleTasks.value.flatMap { (_, tasks) -> tasks.map { it.id.id } }
}

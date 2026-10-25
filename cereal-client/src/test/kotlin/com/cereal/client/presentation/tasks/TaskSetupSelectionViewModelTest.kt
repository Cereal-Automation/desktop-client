package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.task.TaskStatus
import fixtures.TasksScreenWorld
import fixtures.idle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

/** Selection policy: a task setup is always selected while any exist (see [TasksScreenWorld]). */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskSetupSelectionViewModelTest {
    private val world = TasksScreenWorld(StandardTestDispatcher())
    private val viewModels = mutableListOf<TasksViewModel>()

    /** Opens the Tasks screen; calling it again re-opens it within the same app session. */
    private fun open(): TasksViewModel =
        world.viewModel().also {
            viewModels += it
            world.dispatcher.scheduler.advanceUntilIdle()
        }

    private val TasksViewModel.selectedSetupId get() = hierarchicalListViewModel.selectedChild?.id

    @AfterEach
    fun tearDown() {
        viewModels.forEach { it.dispose() }
    }

    @Test
    fun `selects the first setup when tasks opens`() =
        runTest {
            world.setup("setup-1", null, idle())
            world.setup("setup-2", null, idle())

            val viewModel = open()

            assertEquals("setup-1", viewModel.selectedSetupId)
        }

    @Test
    fun `re-opening tasks selects the setup last selected this session`() =
        runTest {
            world.setup("setup-1", null, idle())
            val second = world.setup("setup-2", null, idle())
            val firstVisit = open()
            firstVisit.hierarchicalListViewModel.selectChild(second)
            firstVisit.dispose()

            val viewModel = open()

            assertEquals("setup-2", viewModel.selectedSetupId)
        }

    @Test
    fun `deleting the selected setup selects the first remaining one`() =
        runTest {
            world.setup("setup-1", null, idle())
            world.setup("setup-2", null, idle())
            val third = world.setup("setup-3", null, idle())
            val viewModel = open()
            viewModel.hierarchicalListViewModel.selectChild(third)

            viewModel.confirmScriptInstanceDeletion(third)
            world.dispatcher.scheduler.advanceUntilIdle()

            assertEquals("setup-1", viewModel.selectedSetupId)
            assertIs<TaskViewState.DetailViewState.Filled>(viewModel.tasksViewState.value)
        }

    @Test
    fun `a newly created setup is selected and none of its tasks start`() =
        runTest {
            world.setup("setup-1", null, idle())
            val work = world.group("Work")
            val viewModel = open()

            val created = world.setup("setup-2", work, idle(), idle())
            viewModel.onScriptInstanceCreated(created)
            world.dispatcher.scheduler.advanceUntilIdle()

            assertEquals("setup-2", viewModel.selectedSetupId)
            val detail = assertIs<TaskViewState.DetailViewState.Filled>(viewModel.tasksViewState.value)
            val tasks = detail.scriptInstances.flatMap { it.tasks }
            assertEquals(2, tasks.size)
            assertTrue(tasks.all { it.id.status is TaskStatus.Idle })
        }

    @Test
    fun `creating a group keeps a setup selected`() =
        runTest {
            world.setup("setup-1", null, idle())
            val viewModel = open()

            viewModel.createGroup("Work")
            world.dispatcher.scheduler.advanceUntilIdle()

            assertEquals("setup-1", viewModel.selectedSetupId)
            assertIs<TaskViewState.DetailViewState.Filled>(viewModel.tasksViewState.value)
        }

    @Test
    fun `clearing the selection while setups exist selects the last selected setup`() =
        runTest {
            world.setup("setup-1", null, idle())
            val second = world.setup("setup-2", null, idle())
            val viewModel = open()
            viewModel.hierarchicalListViewModel.selectChild(second)

            // What deleting the selected setup's group does, without waiting for the list to refresh.
            viewModel.hierarchicalListViewModel.deselectAll()

            assertEquals("setup-2", viewModel.selectedSetupId)
            assertIs<TaskViewState.DetailViewState.Filled>(viewModel.tasksViewState.value)
        }
}

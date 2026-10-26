package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.task.TaskSetupSort
import fixtures.TasksScreenWorld
import fixtures.errored
import fixtures.idle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

/** The sort dropdown's order of each group's task setups, on the real graph (see [TasksScreenWorld]). */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskSortViewModelTest {
    private val world = TasksScreenWorld(StandardTestDispatcher())
    private val viewModels = mutableListOf<TasksViewModel>()

    private fun start(): TasksViewModel =
        world.viewModel().also {
            viewModels += it
            settle()
        }

    private fun settle() = world.dispatcher.scheduler.advanceUntilIdle()

    @AfterEach
    fun tearDown() {
        viewModels.forEach { it.dispose() }
    }

    private fun TasksViewModel.list(): TaskViewState.ListState.Filled = assertIs<TaskViewState.ListState.Filled>(listState.value)

    private fun TasksViewModel.setupIds(): List<String> = list().groups.flatMap { it.setups }.map { it.setupId }

    @Test
    fun `a picked sort reorders the setups and is still picked on the next visit`() =
        runTest {
            world.setup("b", null, idle(), name = "Bravo")
            world.setup("a", null, idle(), name = "Alpha")
            val viewModel = start()
            assertEquals(TaskSetupSort.NEWEST_FIRST, viewModel.list().sort)
            assertEquals(listOf("a", "b"), viewModel.setupIds())

            viewModel.onSortSelected(TaskSetupSort.OLDEST_FIRST)
            settle()

            assertEquals(listOf("b", "a"), viewModel.setupIds())
            val nextVisit = start()
            assertEquals(TaskSetupSort.OLDEST_FIRST, nextVisit.list().sort)
            assertEquals(listOf("b", "a"), nextVisit.setupIds())
        }

    @Test
    fun `needs attention moves a setup up as soon as one of its tasks fails`() =
        runTest {
            world.setup("old", null, idle())
            world.setup("new", null, idle())
            val viewModel = start()
            viewModel.onSortSelected(TaskSetupSort.NEEDS_ATTENTION)
            settle()
            assertEquals(listOf("new", "old"), viewModel.setupIds())

            world.tasksRepository.addStatusHistory("old-task-1", errored())
            settle()

            assertEquals(listOf("old", "new"), viewModel.setupIds())
        }
}

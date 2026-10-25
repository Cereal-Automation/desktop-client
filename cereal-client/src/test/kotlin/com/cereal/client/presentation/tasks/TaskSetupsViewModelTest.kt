package com.cereal.client.presentation.tasks

import fixtures.TasksScreenWorld
import fixtures.idle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

/** [TasksViewModel] on the real interactor graph with in-memory repositories (see [TasksScreenWorld]). */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskSetupsViewModelTest {
    private val world = TasksScreenWorld(StandardTestDispatcher())
    private var viewModel: TasksViewModel? = null

    private fun start(): TasksViewModel =
        world.viewModel().also {
            viewModel = it
            world.dispatcher.scheduler.advanceUntilIdle()
        }

    @AfterEach
    fun tearDown() {
        viewModel?.dispose()
    }

    @Test
    fun `shows the empty state while there are no task setups`() =
        runTest {
            val viewModel = start()

            assertIs<TaskViewState.GroupViewState.Empty>(viewModel.hierarchicalListState.value)
        }

    @Test
    fun `shows the list once a task setup exists`() =
        runTest {
            world.setup("setup-1", null, idle())

            val viewModel = start()

            assertIs<TaskViewState.GroupViewState.Filled<*, *>>(viewModel.hierarchicalListState.value)
        }

    @Test
    fun `new task setup opens script selection for the group of the selected setup`() =
        runTest {
            world.setup("setup-1", null, idle())
            val work = world.group("Work")
            val workSetup = world.setup("setup-2", work, idle())
            val viewModel = start()
            viewModel.hierarchicalListViewModel.selectChild(workSetup)

            viewModel.onAddScriptClicked()
            world.dispatcher.scheduler.advanceUntilIdle()

            val dialog = assertIs<TaskViewState.DialogState.SelectingScript>(viewModel.dialogState.value)
            assertEquals(work, dialog.scriptPackageGroup)
        }
}

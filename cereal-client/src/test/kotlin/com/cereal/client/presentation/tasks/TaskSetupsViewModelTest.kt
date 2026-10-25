package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.presentation.view.group.GroupListItemContent
import com.cereal.client.presentation.view.group.HierarchicalListItem
import com.cereal.client.presentation.view.group.ItemStatus
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `a setup card shows a status dot for its most urgent task instead of a task count`() =
        runTest {
            val idleSetup = world.setup("idle", null, idle(), succeeded())
            val runningSetup = world.setup("running", null, idle(), running())
            val erroredSetup = world.setup("errored", null, running(), errored())

            val viewModel = start()

            assertEquals(ItemStatus.IDLE, viewModel.setupCard(idleSetup).status)
            assertEquals(ItemStatus.RUNNING, viewModel.setupCard(runningSetup).status)
            assertEquals(ItemStatus.ERROR, viewModel.setupCard(erroredSetup).status)
            assertNull(viewModel.setupCard(runningSetup).badge)
        }

    @Test
    fun `a setup needs attention only when a task errored or waits for the user`() =
        runTest {
            val idleSetup = world.setup("idle", null, idle(), idle())
            val runningSetup = world.setup("running", null, running())
            val erroredSetup = world.setup("errored", null, idle(), errored())
            val waitingSetup = world.setup("waiting", null, idle(), running())
            world.awaitUserInput("waiting-task-2")

            val viewModel = start()

            assertFalse(viewModel.setupCard(idleSetup).needsAttention)
            assertFalse(viewModel.setupCard(runningSetup).needsAttention)
            assertTrue(viewModel.setupCard(erroredSetup).needsAttention)
            assertTrue(viewModel.setupCard(waitingSetup).needsAttention)
        }

    private fun TasksViewModel.setupCard(setup: ScriptPackageInstance): GroupListItemContent<*> =
        assertIs<TaskViewState.GroupViewState.Filled<*, *>>(hierarchicalListState.value)
            .hierarchicalViewModel.state.value
            .filterIsInstance<HierarchicalListItem.Parent<*, *>>()
            .flatMap { it.children }
            .single { it.content.id == setup }
            .content
}

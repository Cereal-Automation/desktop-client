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

/** The detail header's "···" menu acts on the selected task setup, like the list context menu. */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskSetupHeaderActionsViewModelTest {
    private val world = TasksScreenWorld(StandardTestDispatcher())
    private var viewModel: TasksViewModel? = null

    private fun start(): TasksViewModel =
        world.viewModel().also {
            viewModel = it
            world.dispatcher.scheduler.advanceUntilIdle()
        }

    private fun settle() = world.dispatcher.scheduler.advanceUntilIdle()

    @AfterEach
    fun tearDown() {
        viewModel?.dispose()
    }

    @Test
    fun `delete from the header asks for confirmation, then removes the selected setup`() =
        runTest {
            val setup = world.setup("setup-1", null, idle())
            val viewModel = start()
            viewModel.hierarchicalListViewModel.selectChild(setup)

            viewModel.onHeaderMenuOptionSelected(MenuOption.DELETE)

            val dialog = assertIs<TaskViewState.DialogState.ConfirmScriptInstanceDeletion>(viewModel.dialogState.value)
            assertEquals(setup, dialog.scriptPackageInstance)
            assertEquals(listOf(setup), world.scriptInstances.getScriptPackageInstances())

            viewModel.confirmScriptInstanceDeletion(dialog.scriptPackageInstance)
            settle()

            assertEquals(emptyList<Any>(), world.scriptInstances.getScriptPackageInstances())
        }

    @Test
    fun `move to group from the header offers the groups, then moves the selected setup`() =
        runTest {
            val default = world.defaultGroup()
            val work = world.group("Work")
            val setup = world.setup("setup-1", null, idle())
            val viewModel = start()
            viewModel.hierarchicalListViewModel.selectChild(setup)

            viewModel.onHeaderMenuOptionSelected(MenuOption.MOVE_TO_GROUP)

            val dialog = assertIs<TaskViewState.DialogState.MovingScript>(viewModel.dialogState.value)
            assertEquals(default, dialog.currentGroup)
            assertEquals(setOf(default, work), dialog.availableGroups.toSet())

            viewModel.changeGroup(dialog.scriptPackageInstance, work)
            settle()

            assertEquals(listOf(setup), world.scriptInstances.getScriptPackageInstancesInGroup(work))
            assertEquals(emptyList<Any>(), world.scriptInstances.getScriptPackageInstancesInGroup(default))
        }

    @Test
    fun `duplicate from the header opens script selection prefilled with the selected setup`() =
        runTest {
            val work = world.group("Work")
            world.setup("setup-1", null, idle())
            val setup = world.setup("setup-2", work, idle())
            val viewModel = start()
            viewModel.hierarchicalListViewModel.selectChild(setup)

            viewModel.onHeaderMenuOptionSelected(MenuOption.DUPLICATE)

            val dialog = assertIs<TaskViewState.DialogState.DuplicatingScript>(viewModel.dialogState.value)
            assertEquals(setup, dialog.scriptPackageInstance)
            assertEquals(work, dialog.scriptPackageGroup)
        }
}

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

/** A task setup row's "···" menu acts on that row's setup. */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskSetupMenuViewModelTest {
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
    fun `delete from the setup menu asks for confirmation, then removes the setup`() =
        runTest {
            val setup = world.setup("setup-1", null, idle())
            val viewModel = start()

            viewModel.onSetupMenuOptionSelected(setup.id, SetupMenuOption.DELETE)

            val dialog = assertIs<TaskViewState.DialogState.ConfirmScriptInstanceDeletion>(viewModel.dialogState.value)
            assertEquals(setup, dialog.scriptPackageInstance)
            assertEquals(listOf(setup), world.scriptInstances.getScriptPackageInstances())

            viewModel.confirmScriptInstanceDeletion(dialog.scriptPackageInstance)
            settle()

            assertEquals(emptyList<Any>(), world.scriptInstances.getScriptPackageInstances())
        }

    @Test
    fun `move to group from the setup menu offers the groups, then moves the setup`() =
        runTest {
            val default = world.defaultGroup()
            val work = world.group("Work")
            val setup = world.setup("setup-1", null, idle())
            val viewModel = start()

            viewModel.onSetupMenuOptionSelected(setup.id, SetupMenuOption.MOVE_TO_GROUP)

            val dialog = assertIs<TaskViewState.DialogState.MovingScript>(viewModel.dialogState.value)
            assertEquals(default, dialog.currentGroup)
            assertEquals(setOf(default, work), dialog.availableGroups.toSet())

            viewModel.changeGroup(dialog.scriptPackageInstance, work)
            settle()

            assertEquals(listOf(setup), world.scriptInstances.getScriptPackageInstancesInGroup(work))
            assertEquals(emptyList<Any>(), world.scriptInstances.getScriptPackageInstancesInGroup(default))
        }

    @Test
    fun `duplicate from the setup menu opens script selection prefilled with the setup`() =
        runTest {
            val work = world.group("Work")
            world.setup("setup-1", null, idle())
            val setup = world.setup("setup-2", work, idle())
            val viewModel = start()

            viewModel.onSetupMenuOptionSelected(setup.id, SetupMenuOption.DUPLICATE)

            val dialog = assertIs<TaskViewState.DialogState.DuplicatingScript>(viewModel.dialogState.value)
            assertEquals(setup, dialog.scriptPackageInstance)
            assertEquals(work, dialog.scriptPackageGroup)
        }

    @Test
    fun `view configuration from the setup menu shows the setup's configuration`() =
        runTest {
            val setup = world.setup("setup-1", null, idle())
            val viewModel = start()

            viewModel.onSetupMenuOptionSelected(setup.id, SetupMenuOption.VIEW_CONFIGURATION)

            val dialog = assertIs<TaskViewState.DialogState.ViewScriptConfiguration>(viewModel.dialogState.value)
            assertEquals(setup, dialog.scriptPackageInstance)
        }

    @Test
    fun `closing the delete confirmation keeps the setup`() =
        runTest {
            val setup = world.setup("setup-1", null, idle())
            val viewModel = start()

            viewModel.onSetupMenuOptionSelected(setup.id, SetupMenuOption.DELETE)
            viewModel.closeDialog()
            settle()

            assertEquals(TaskViewState.DialogState.Hidden, viewModel.dialogState.value)
            assertEquals(listOf(setup), world.scriptInstances.getScriptPackageInstances())
        }
}

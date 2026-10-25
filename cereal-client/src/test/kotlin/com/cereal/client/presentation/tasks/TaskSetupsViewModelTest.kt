package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.presentation.error.ErrorAction
import fixtures.TasksScreenWorld
import fixtures.idle
import fixtures.running
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `edit opens the configuration dialog for the selected setup`() =
        runTest {
            val setup = world.setup("setup-1", null, idle())
            val viewModel = start()
            viewModel.hierarchicalListViewModel.selectChild(setup)
            world.dispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.editEnabled)
            viewModel.onEditClicked()

            val dialog = assertIs<TaskViewState.DialogState.EditingScript>(viewModel.dialogState.value)
            assertEquals("setup-1", dialog.scriptPackageInstance.id)
        }

    @Test
    fun `saving an edit updates the setup in place and keeps its tasks`() =
        runTest {
            val setup = world.setup("setup-1", null, idle())
            val taskBefore = world.tasksRepository.getTask("setup-1-task-1")!!
            val viewModel = start()
            viewModel.hierarchicalListViewModel.selectChild(setup)
            viewModel.onEditClicked()
            val newConfiguration = mapOf("name" to ConfigValue.StringValue("edited"))

            viewModel.saveEdit(setup.copy(mainConfiguration = newConfiguration))
            world.dispatcher.scheduler.advanceUntilIdle()

            assertIs<TaskViewState.DialogState.Hidden>(viewModel.dialogState.value)
            val stored = world.scriptInstances.getScriptPackageInstance("setup-1")
            assertEquals(newConfiguration, stored.mainConfiguration)
            assertEquals(setup.createdAt, stored.createdAt)
            val task = world.tasksRepository.getTask("setup-1-task-1")!!
            assertEquals(taskBefore.statusHistory, task.statusHistory)
            assertEquals(taskBefore.createdAt, task.createdAt)
            assertEquals(newConfiguration, task.configuration)
            assertEquals(newConfiguration, task.scriptInstance.packageInstance.mainConfiguration)
        }

    @Test
    fun `edit is disabled and rejected while a task of the setup runs`() =
        runTest {
            val setup = world.setup("setup-1", null, running())
            val viewModel = start()
            viewModel.hierarchicalListViewModel.selectChild(setup)
            world.dispatcher.scheduler.advanceUntilIdle()

            assertFalse(viewModel.editEnabled)
            viewModel.saveEdit(setup.copy(mainConfiguration = mapOf("name" to ConfigValue.StringValue("edited"))))
            world.dispatcher.scheduler.advanceUntilIdle()

            assertIs<ErrorAction.Message>(world.errorResolver.errorAction.value)
            assertEquals(emptyMap<String, ConfigValue>(), world.scriptInstances.getScriptPackageInstance("setup-1").mainConfiguration)
        }
}

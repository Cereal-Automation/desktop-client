package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.presentation.view.group.HierarchicalListItem
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

    @Test
    fun `lists task setups flat while only the default group exists`() =
        runTest {
            world.setup("setup-1", null, idle())
            world.setup("setup-2", null, idle())

            val viewModel = start()

            assertEquals(listOf("setup-1", "setup-2"), viewModel.listLabels())
        }

    @Test
    fun `shows every group with its header once a second group exists`() =
        runTest {
            val default = world.defaultGroup()
            val work = world.group("Work")
            world.setup("setup-1", default, idle())
            world.setup("setup-2", work, idle())

            val viewModel = start()

            assertEquals(listOf("Default", "setup-1", "Work", "setup-2"), viewModel.listLabels())
        }

    @Test
    fun `switches between flat and grouped as a second group comes and goes`() =
        runTest {
            world.setup("setup-1", null, idle())
            val viewModel = start()

            val work = world.group("Work")
            world.dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf("Default", "setup-1", "Work"), viewModel.listLabels())

            world.tasksRepository.deleteTaskGroup(work)
            world.dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf("setup-1"), viewModel.listLabels())
        }

    /** Group names (headers) and setup ids, in the order the list shows them. */
    private fun TasksViewModel.listLabels(): List<String> = hierarchicalListViewModel.state.value.map { it.label() }

    private fun HierarchicalListItem<*, *>.label(): String =
        when (this) {
            is HierarchicalListItem.Parent -> (content.id as ScriptPackageGroup).name
            is HierarchicalListItem.Child -> (content.id as ScriptPackageInstance).id
        }
}

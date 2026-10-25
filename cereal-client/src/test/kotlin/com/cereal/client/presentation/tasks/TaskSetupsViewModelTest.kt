package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.presentation.error.ErrorAction
import com.cereal.client.presentation.view.group.GroupListItemContent
import com.cereal.client.presentation.view.group.HierarchicalListItem
import com.cereal.client.presentation.view.group.ItemStatus
import fixtures.TasksScreenWorld
import fixtures.errored
import fixtures.idle
import fixtures.proxyGroup
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

    @Test
    fun `plus adds an idle task and persists the new count`() =
        runTest {
            val setup = world.setup("setup-1", null, idle(), configuration = proxyGroup())
            val viewModel = start()
            viewModel.hierarchicalListViewModel.selectChild(setup)
            world.dispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, viewModel.concurrentTaskStepper.value?.count)

            viewModel.increaseConcurrentTasks()
            world.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, world.scriptInstances.getScriptPackageInstance("setup-1").numberOfConcurrentTasks)
            assertEquals(2, viewModel.concurrentTaskStepper.value?.count)
            val tasks = world.tasksRepository.getTasks(setup)
            assertEquals(2, tasks.size)
            assertTrue(tasks.all { it.status is TaskStatus.Idle })
        }

    @Test
    fun `minus removes the highest-numbered task that is not running`() =
        runTest {
            val setup = world.setup("setup-1", null, idle(), succeeded(), running(), configuration = proxyGroup())
            val viewModel = start()
            viewModel.hierarchicalListViewModel.selectChild(setup)
            world.dispatcher.scheduler.advanceUntilIdle()

            viewModel.decreaseConcurrentTasks()
            world.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, viewModel.concurrentTaskStepper.value?.count)
            assertEquals(2, world.scriptInstances.getScriptPackageInstance("setup-1").numberOfConcurrentTasks)
            assertEquals(
                listOf("setup-1-task-1", "setup-1-task-3"),
                world.tasksRepository
                    .getTasks(setup)
                    .map { it.id }
                    .sorted(),
            )
        }

    @Test
    fun `minus is disabled and rejected when only running tasks are left to remove`() =
        runTest {
            val setup = world.setup("setup-1", null, running(), running(), configuration = proxyGroup())
            val viewModel = start()
            viewModel.hierarchicalListViewModel.selectChild(setup)
            world.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(false, viewModel.concurrentTaskStepper.value?.canDecrease)
            viewModel.decreaseConcurrentTasks()
            world.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, world.scriptInstances.getScriptPackageInstance("setup-1").numberOfConcurrentTasks)
            assertEquals(2, world.tasksRepository.getTasks(setup).size)
        }

    @Test
    fun `minus is disabled at one task`() =
        runTest {
            val setup = world.setup("setup-1", null, idle(), configuration = proxyGroup())
            val viewModel = start()
            viewModel.hierarchicalListViewModel.selectChild(setup)
            world.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(ConcurrentTaskStepperState(1, canDecrease = false, canIncrease = true), viewModel.concurrentTaskStepper.value)
            viewModel.decreaseConcurrentTasks()
            world.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, world.tasksRepository.getTasks(setup).size)
        }

    @Test
    fun `no stepper for a setup without a proxy group or dataset, which always runs one task`() =
        runTest {
            val setup = world.setup("setup-1", null, idle())
            val viewModel = start()
            viewModel.hierarchicalListViewModel.selectChild(setup)
            world.dispatcher.scheduler.advanceUntilIdle()

            assertNull(viewModel.concurrentTaskStepper.value)
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
            .flatMap { item ->
                when (item) {
                    is HierarchicalListItem.Parent -> item.children
                    is HierarchicalListItem.Child -> listOf(item)
                }
            }.single { it.content.id == setup }
            .content

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

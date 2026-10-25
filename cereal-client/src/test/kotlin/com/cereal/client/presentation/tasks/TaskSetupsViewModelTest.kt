package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.TaskStatus
import fixtures.TasksScreenWorld
import fixtures.idle
import fixtures.proxyGroup
import fixtures.running
import fixtures.succeeded
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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

            assertIs<TaskViewState.ListState.Empty>(viewModel.listState.value)
        }

    @Test
    fun `shows the list once a task setup exists`() =
        runTest {
            world.setup("setup-1", null, idle())

            val viewModel = start()

            assertIs<TaskViewState.ListState.Filled>(viewModel.listState.value)
        }

    @Test
    fun `new task setup opens script selection for the default group`() =
        runTest {
            val default = world.defaultGroup()
            val work = world.group("Work")
            world.setup("setup-1", work, idle())
            val viewModel = start()

            viewModel.onAddScriptClicked()
            world.dispatcher.scheduler.advanceUntilIdle()

            val dialog = assertIs<TaskViewState.DialogState.SelectingScript>(viewModel.dialogState.value)
            assertEquals(default, dialog.scriptPackageGroup)
        }

    @Test
    fun `plus adds an idle task and persists the new count`() =
        runTest {
            val setup = world.setup("setup-1", null, idle(), configuration = proxyGroup())
            val viewModel = start()
            assertEquals(1, viewModel.stepperOf(setup)?.count)

            viewModel.increaseConcurrentTasks(setup.id)
            world.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, world.scriptInstances.getScriptPackageInstance("setup-1").numberOfConcurrentTasks)
            assertEquals(2, viewModel.stepperOf(setup)?.count)
            val tasks = world.tasksRepository.getTasks(setup)
            assertEquals(2, tasks.size)
            assertTrue(tasks.all { it.status is TaskStatus.Idle })
        }

    @Test
    fun `minus removes the highest-numbered task that is not running`() =
        runTest {
            val setup = world.setup("setup-1", null, idle(), succeeded(), running(), configuration = proxyGroup())
            val viewModel = start()

            viewModel.decreaseConcurrentTasks(setup.id)
            world.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, viewModel.stepperOf(setup)?.count)
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

            assertEquals(false, viewModel.stepperOf(setup)?.canDecrease)
            viewModel.decreaseConcurrentTasks(setup.id)
            world.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, world.scriptInstances.getScriptPackageInstance("setup-1").numberOfConcurrentTasks)
            assertEquals(2, world.tasksRepository.getTasks(setup).size)
        }

    @Test
    fun `minus is disabled at one task`() =
        runTest {
            val setup = world.setup("setup-1", null, idle(), configuration = proxyGroup())
            val viewModel = start()

            assertEquals(ConcurrentTaskStepperState(1, canDecrease = false, canIncrease = true), viewModel.stepperOf(setup))
            viewModel.decreaseConcurrentTasks(setup.id)
            world.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, world.tasksRepository.getTasks(setup).size)
        }

    @Test
    fun `no stepper for a setup without a proxy group or dataset, which always runs one task`() =
        runTest {
            val setup = world.setup("setup-1", null, idle())
            val viewModel = start()

            assertNull(viewModel.stepperOf(setup))
        }

    private fun TasksViewModel.stepperOf(setup: ScriptPackageInstance): ConcurrentTaskStepperState? =
        assertIs<TaskViewState.ListState.Filled>(listState.value)
            .groups
            .flatMap { it.setups }
            .single { it.setupId == setup.id }
            .stepper
}

package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.task.TaskStatus
import fixtures.TasksScreenWorld
import fixtures.errored
import fixtures.idle
import fixtures.proxyGroup
import fixtures.running
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

/** Per-setup actions act on the task setup passed in, never on another one. */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskSetupActionsViewModelTest {
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
    fun `start all starts only the passed setup's tasks`() =
        runTest {
            val selected = world.setup("selected", null, idle())
            val other = world.setup("other", null, idle())
            val viewModel = start()

            viewModel.startAllTasks(other.id)
            settle()

            assertTrue(
                world.tasksRepository
                    .getTask("other-task-1")!!
                    .statusHistory
                    .any { it is TaskStatus.Running },
            )
            assertEquals(
                1,
                world.tasksRepository
                    .getTask("selected-task-1")!!
                    .statusHistory.size,
            )
        }

    @Test
    fun `stop all stops only the passed setup's running tasks`() =
        runTest {
            val selected = world.setup("selected", null, running())
            val other = world.setup("other", null, running())
            val selectedJob = Job().also { world.tasksRepository.setTaskJob("selected-task-1", it) }
            val otherJob = Job().also { world.tasksRepository.setTaskJob("other-task-1", it) }
            val viewModel = start()

            viewModel.stopAllTasks(other.id)
            settle()

            assertTrue(otherJob.isCancelled)
            assertFalse(selectedJob.isCancelled)
        }

    @Test
    fun `restart failed restarts only the passed setup's failed tasks`() =
        runTest {
            val selected = world.setup("selected", null, errored())
            val other = world.setup("other", null, errored())
            val viewModel = start()

            viewModel.onSetupMenuOptionSelected(other.id, SetupMenuOption.RESTART_FAILED)
            settle()

            assertTrue(
                world.tasksRepository
                    .getTask("other-task-1")!!
                    .statusHistory
                    .any { it is TaskStatus.Running },
            )
            assertEquals(
                1,
                world.tasksRepository
                    .getTask("selected-task-1")!!
                    .statusHistory.size,
            )
        }

    @Test
    fun `the stepper changes only the passed setup's task count`() =
        runTest {
            // The other setup has no stepper (it always runs one task), so the step must not depend on it.
            val selected = world.setup("selected", null, idle())
            val other = world.setup("other", null, idle(), idle(), configuration = proxyGroup())
            val viewModel = start()

            viewModel.increaseConcurrentTasks(other.id)
            settle()
            assertEquals(3, world.tasksRepository.getTasks(other).size)

            viewModel.decreaseConcurrentTasks(other.id)
            settle()
            assertEquals(2, world.tasksRepository.getTasks(other).size)
            assertEquals(2, world.scriptInstances.getScriptPackageInstance("other").numberOfConcurrentTasks)
            assertEquals(1, world.tasksRepository.getTasks(selected).size)
            assertEquals(1, world.scriptInstances.getScriptPackageInstance("selected").numberOfConcurrentTasks)
        }

    @Test
    fun `edit is offered only while none of the setup's tasks runs, and opens the passed setup`() =
        runTest {
            world.setup("selected", null, idle())
            val idleSetup = world.setup("idle", null, idle())
            val runningSetup = world.setup("running", null, running())
            val viewModel = start()

            assertFalse(viewModel.section(runningSetup.id).editable)
            assertTrue(viewModel.section(idleSetup.id).editable)

            viewModel.onSetupMenuOptionSelected(idleSetup.id, SetupMenuOption.EDIT)
            val dialog = assertIs<TaskViewState.DialogState.EditingScript>(viewModel.dialogState.value)
            assertEquals("idle", dialog.scriptPackageInstance.id)
        }

    private fun TasksViewModel.section(setupId: String): TaskSetupSection = assertIs<TaskViewState.ListState.Filled>(listState.value).groups.flatMap { it.setups }.single { it.setupId == setupId }
}

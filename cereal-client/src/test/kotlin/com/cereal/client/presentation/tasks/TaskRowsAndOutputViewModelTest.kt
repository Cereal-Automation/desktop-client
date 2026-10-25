package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.presentation.tasks.model.TaskUiModel
import fixtures.TasksScreenWorld
import fixtures.aScriptInstance
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/** Task row content and the Output header (#208), on the real graph (see [TasksScreenWorld]). */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class TaskRowsAndOutputViewModelTest {
    private val world = TasksScreenWorld(StandardTestDispatcher())
    private var viewModel: TasksViewModel? = null

    private fun start(selected: ScriptPackageInstance): TasksViewModel =
        world.viewModel().also {
            viewModel = it
            world.dispatcher.scheduler.advanceUntilIdle()
            it.hierarchicalListViewModel.selectChild(selected)
            world.dispatcher.scheduler.advanceUntilIdle()
        }

    private fun TasksViewModel.rows(): List<TaskUiModel> = assertIs<TaskViewState.DetailViewState.Filled>(tasksViewState.value).scriptInstances.flatMap { it.tasks }

    private fun TasksViewModel.row(taskId: String): TaskUiModel = rows().single { it.id.id == taskId }

    @AfterEach
    fun tearDown() {
        viewModel?.dispose()
    }

    @Test
    fun `an idle task is marked idle and other tasks show their latest message`() =
        runTest {
            val setup = world.setup("s", null, idle(), running("Checking out"), succeeded("Bought it"), errored("Sold out"))

            val viewModel = start(setup)

            assertTrue(viewModel.row("s-task-1").isIdle)
            assertFalse(viewModel.row("s-task-2").isIdle)
            assertEquals("Checking out", viewModel.row("s-task-2").message)
            assertEquals("Bought it", viewModel.row("s-task-3").message)
            assertEquals("Sold out", viewModel.row("s-task-4").message)
        }

    @Test
    fun `a row carries the time of its current status`() =
        runTest {
            val changedAt = Clock.System.now() - 12.seconds
            val setup = world.setup("s", null, TaskStatus.Running("Monitoring", changedAt))

            val viewModel = start(setup)

            assertEquals(changedAt, viewModel.row("s-task-1").statusSince)
        }

    @Test
    fun `a task without status history shows the time since it was created`() =
        runTest {
            val setup = world.setup("s", null, idle())
            val createdAt = Clock.System.now() - 3.seconds
            world.tasksRepository.addAllTasks(
                listOf(
                    JobTask(
                        id = "never-run",
                        scriptInstance = aScriptInstance(setup),
                        configuration = emptyMap(),
                        createdAt = createdAt,
                    ),
                ),
            )

            val viewModel = start(setup)

            assertEquals(createdAt, viewModel.row("never-run").statusSince)
        }

    @Test
    fun `the output header names the selected task by its number`() =
        runTest {
            val setup = world.setup("s", null, idle(), running("Monitoring"))
            val viewModel = start(setup)

            viewModel.onTaskSelected(viewModel.row("s-task-2").id)

            assertEquals(2, viewModel.selectedTaskNumber)
        }

    @Test
    fun `the output header names no task while none is selected`() =
        runTest {
            val setup = world.setup("s", null, idle())
            val viewModel = start(setup)

            viewModel.onTaskSelected(null)

            assertNull(viewModel.selectedTaskNumber)
        }
}

package com.cereal.client.presentation.tasks

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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/** Task row content (#208), on the real graph (see [TasksScreenWorld]). */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class TaskRowsViewModelTest {
    private val world = TasksScreenWorld(StandardTestDispatcher())
    private var viewModel: TasksViewModel? = null

    private fun start(): TasksViewModel =
        world.viewModel().also {
            viewModel = it
            world.dispatcher.scheduler.advanceUntilIdle()
        }

    private fun TasksViewModel.rows(): List<TaskUiModel> =
        assertIs<TaskViewState.ListState.Filled>(listState.value)
            .groups
            .flatMap { it.setups }
            .flatMap { it.scripts }
            .flatMap { it.tasks }

    private fun TasksViewModel.row(taskId: String): TaskUiModel = rows().single { it.id.id == taskId }

    @AfterEach
    fun tearDown() {
        viewModel?.dispose()
    }

    @Test
    fun `an idle task is marked idle and other tasks show their latest message`() =
        runTest {
            world.setup("s", null, idle(), running("Checking out"), succeeded("Bought it"), errored("Sold out"))

            val viewModel = start()

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
            world.setup("s", null, TaskStatus.Running("Monitoring", changedAt))

            val viewModel = start()

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

            val viewModel = start()

            assertEquals(createdAt, viewModel.row("never-run").statusSince)
        }
}

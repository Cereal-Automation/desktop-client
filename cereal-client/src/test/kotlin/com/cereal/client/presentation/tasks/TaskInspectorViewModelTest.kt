package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.logging.LoggingPriority
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.presentation.tasks.model.TaskUiModel
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date
import kotlin.test.assertIs

/** The task inspector (#220): selecting a task row, and what the inspector shows, on the real graph (see [TasksScreenWorld]). */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskInspectorViewModelTest {
    private val world = TasksScreenWorld(StandardTestDispatcher())
    private var viewModel: TasksViewModel? = null

    private fun start(): TasksViewModel =
        world.viewModel().also {
            viewModel = it
            settle()
        }

    private fun settle() = world.dispatcher.scheduler.advanceUntilIdle()

    @AfterEach
    fun tearDown() {
        viewModel?.dispose()
    }

    private fun TasksViewModel.row(taskId: String): TaskUiModel =
        assertIs<TaskViewState.ListState.Filled>(listState.value)
            .groups
            .flatMap { it.setups }
            .flatMap { it.scripts }
            .flatMap { it.tasks }
            .single { it.id.id == taskId }

    private fun TasksViewModel.task(taskId: String): Task = row(taskId).id

    private fun TasksViewModel.inspectedTaskId(): String? =
        inspector.value
            ?.task
            ?.id
            ?.id

    @Test
    fun `clicking a row opens the inspector for that task and clicking it again closes it`() =
        runTest {
            world.setup("s", null, idle(), idle())
            val viewModel = start()

            viewModel.onTaskClicked(viewModel.task("s-task-2"))
            assertEquals("s-task-2", viewModel.inspectedTaskId())

            viewModel.onTaskClicked(viewModel.task("s-task-1"))
            assertEquals("s-task-1", viewModel.inspectedTaskId())

            viewModel.onTaskClicked(viewModel.task("s-task-1"))
            assertNull(viewModel.inspector.value)
        }

    @Test
    fun `closing the inspector clears the selection`() =
        runTest {
            world.setup("s", null, idle())
            val viewModel = start()
            viewModel.onTaskClicked(viewModel.task("s-task-1"))

            viewModel.closeInspector()

            assertNull(viewModel.inspector.value)
            assertNull(viewModel.selectedTask.value)
        }

    @Test
    fun `the inspector shows the selected task's number, status and latest message`() =
        runTest {
            world.setup("s", null, idle(), errored("Sold out"))
            val viewModel = start()

            viewModel.onTaskClicked(viewModel.task("s-task-2"))

            val task = viewModel.inspector.value!!.task
            assertEquals(2, task.taskNumber)
            assertEquals("Error", task.status)
            assertEquals("Sold out", task.message)
        }

    @Test
    fun `the inspector's actions follow the task's status`() =
        runTest {
            world.setup("s", null, running("Monitoring"), idle(), succeeded(), errored())
            world.setup("supported", null, errored(), supportUrl = "https://github.com/example/script")
            val viewModel = start()

            fun actionsOf(taskId: String): List<TaskInspectorAction> {
                viewModel.onTaskClicked(viewModel.task(taskId))
                return viewModel.inspector.value!!.actions
            }

            assertEquals(listOf(TaskInspectorAction.STOP), actionsOf("s-task-1"))
            assertEquals(listOf(TaskInspectorAction.START), actionsOf("s-task-2"))
            assertEquals(listOf(TaskInspectorAction.RESTART), actionsOf("s-task-3"))
            assertEquals(listOf(TaskInspectorAction.RESTART), actionsOf("s-task-4"))
            assertEquals(listOf(TaskInspectorAction.RESTART, TaskInspectorAction.REPORT_ISSUE), actionsOf("supported-task-1"))
        }

    @Test
    fun `report issue opens the script's support page for the inspected task`() =
        runTest {
            world.setup("s", null, errored("Sold out"), supportUrl = "https://github.com/example/script")
            val viewModel = start()
            viewModel.onTaskClicked(viewModel.task("s-task-1"))

            viewModel.onInspectorAction(TaskInspectorAction.REPORT_ISSUE)
            settle()

            assertTrue(
                world.systemProvider.browsedUrls
                    .single()
                    .startsWith("https://github.com/example/script"),
            )
        }

    @Test
    fun `restarting a succeeded task from the inspector asks for confirmation`() =
        runTest {
            world.setup("s", null, succeeded())
            val viewModel = start()
            viewModel.onTaskClicked(viewModel.task("s-task-1"))

            viewModel.onInspectorAction(TaskInspectorAction.RESTART)

            assertIs<TaskViewState.DialogState.ConfirmTaskRestart>(viewModel.dialogState.value)
        }

    @Test
    fun `starting an idle task from the inspector runs it`() =
        runTest {
            world.setup("s", null, idle())
            val viewModel = start()
            viewModel.onTaskClicked(viewModel.task("s-task-1"))

            viewModel.onInspectorAction(TaskInspectorAction.START)
            settle()

            assertTrue(
                world.tasksRepository
                    .getTask("s-task-1")!!
                    .statusHistory
                    .any { it is TaskStatus.Running },
            )
        }

    @Test
    fun `a row's start button runs its task without opening the inspector`() =
        runTest {
            world.setup("s", null, idle())
            val viewModel = start()

            viewModel.startTask(viewModel.row("s-task-1"))
            settle()

            assertTrue(
                world.tasksRepository
                    .getTask("s-task-1")!!
                    .statusHistory
                    .any { it is TaskStatus.Running },
            )
            assertNull(viewModel.inspector.value)
        }

    @Test
    fun `confirming a row's restart does not open the inspector`() =
        runTest {
            world.setup("s", null, succeeded())
            val viewModel = start()

            viewModel.startTask(viewModel.row("s-task-1"))
            viewModel.confirmTaskRestart(viewModel.row("s-task-1"))

            assertNull(viewModel.inspector.value)
        }

    @Test
    fun `the inspector follows the task's status live`() =
        runTest {
            world.setup("s", null, running("Monitoring"))
            val viewModel = start()
            viewModel.onTaskClicked(viewModel.task("s-task-1"))

            world.tasksRepository.addStatusHistory("s-task-1", errored("Sold out"))
            settle()

            assertEquals(
                "Sold out",
                viewModel.inspector.value!!
                    .task.message,
            )
            assertEquals(listOf(TaskInspectorAction.RESTART), viewModel.inspector.value!!.actions)
        }

    @Test
    fun `deleting the selected task's setup closes the inspector`() =
        runTest {
            val setup = world.setup("s", null, idle())
            world.setup("other", null, idle())
            val viewModel = start()
            viewModel.onTaskClicked(viewModel.task("s-task-1"))

            viewModel.confirmScriptInstanceDeletion(setup)
            settle()

            assertNull(viewModel.inspector.value)
            assertNull(viewModel.selectedTask.value)
        }

    @Test
    fun `the inspector shows the selected task's own log and artifacts, live`() =
        runTest {
            world.setup("s", null, idle(), idle())
            world.logEventRepository.persist(logLine("s-task-1", "first task's line"))
            world.logEventRepository.persist(logLine("s-task-2", "second task's line"))
            world.artifactRepository.emit("s-task-2", "receipt.json", "{}".toByteArray(), "application/json")
            val viewModel = start()

            viewModel.onTaskClicked(viewModel.task("s-task-2"))
            settle()
            assertEquals(listOf("second task's line"), viewModel.logViewerMessages.value.map { it.message })
            assertEquals(listOf("receipt.json"), viewModel.artifacts.value.map { it.name })

            world.logEventRepository.persist(logLine("s-task-2", "another line"))
            world.artifactRepository.emit("s-task-2", "screenshot.png", ByteArray(1), "image/png")
            settle()
            assertEquals(listOf("second task's line", "another line"), viewModel.logViewerMessages.value.map { it.message })
            assertEquals(listOf("receipt.json", "screenshot.png"), viewModel.artifacts.value.map { it.name })

            viewModel.onTaskClicked(viewModel.task("s-task-1"))
            settle()
            assertEquals(listOf("first task's line"), viewModel.logViewerMessages.value.map { it.message })
            assertEquals(emptyList<String>(), viewModel.artifacts.value.map { it.name })
        }

    private var logClock = 0L

    private fun logLine(
        taskId: String,
        message: String,
    ) = LoggingEvent(LoggingPriority.INFO, taskId, message, Date(++logClock))
}

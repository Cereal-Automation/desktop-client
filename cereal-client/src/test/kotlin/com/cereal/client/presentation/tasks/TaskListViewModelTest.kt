package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.task.TaskStatus
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

/** The one list of every task setup, grouped by group, on the real graph (see [TasksScreenWorld]). */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModelTest {
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

    private fun TasksViewModel.groups(): List<TaskGroupSection> = assertIs<TaskViewState.ListState.Filled>(listState.value).groups

    private fun TasksViewModel.section(setupId: String): TaskSetupSection = groups().flatMap { it.setups }.single { it.setupId == setupId }

    /** Group name → setup id → task ids, in list order. */
    private fun TasksViewModel.outline(): Map<String, Map<String, List<String>>> =
        groups().associate { group ->
            group.name to group.setups.associate { setup -> setup.setupId to setup.scripts.flatMap { it.tasks }.map { it.id.id } }
        }

    @Test
    fun `lists every task setup under its group with all its tasks`() =
        runTest {
            val default = world.defaultGroup()
            val work = world.group("Work")
            world.setup("a", default, idle(), idle())
            world.setup("b", work, idle())
            world.setup("c", work, running(), idle(), idle())

            val viewModel = start()

            assertEquals(
                mapOf(
                    "Default" to mapOf("a" to listOf("a-task-1", "a-task-2")),
                    "Work" to mapOf("b" to listOf("b-task-1"), "c" to listOf("c-task-1", "c-task-2", "c-task-3")),
                ),
                viewModel.outline(),
            )
        }

    @Test
    fun `a setup's running, failed and total counts follow its own tasks live`() =
        runTest {
            world.setup("a", null, running(), errored(), errored(), idle())
            world.setup("b", null, running())
            val viewModel = start()

            assertEquals(Triple(1, 2, 4), viewModel.section("a").counts())

            world.tasksRepository.addStatusHistory("a-task-4", running())
            settle()

            assertEquals(Triple(2, 2, 4), viewModel.section("a").counts())
            assertEquals(Triple(1, 0, 1), viewModel.section("b").counts())
        }

    private fun TaskSetupSection.counts() = Triple(runningCount, failedCount, taskCount)

    private suspend fun TasksScreenWorld.wasRestarted(taskId: String) = tasksRepository.getTask(taskId)!!.statusHistory.any { it is TaskStatus.Running }

    @Test
    fun `the header counts every setup and every task`() =
        runTest {
            val work = world.group("Work")
            world.setup("a", null, idle(), idle())
            world.setup("b", work, idle())
            val viewModel = start()

            val list = assertIs<TaskViewState.ListState.Filled>(viewModel.listState.value)

            assertEquals(2 to 3, list.setupCount to list.taskCount)
        }

    @Test
    fun `a setup's toggle offers Stop all while any of its tasks runs, else Start all when one can start`() =
        runTest {
            world.setup("busy", null, idle(), running())
            world.setup("ready", null, idle(), succeeded())
            world.setup("done", null, succeeded(), errored())
            val viewModel = start()

            assertEquals(true to true, viewModel.section("busy").let { it.showStopAll to it.canStartAll })
            assertEquals(false to true, viewModel.section("ready").let { it.showStopAll to it.canStartAll })
            assertEquals(false to false, viewModel.section("done").let { it.showStopAll to it.canStartAll })
        }

    @Test
    fun `a setup's menu offers Restart failed only with failed tasks and Contact support only with a support url`() =
        runTest {
            world.setup("plain", null, idle())
            world.setup("failing", null, errored(), idle(), supportUrl = "https://example.com/support")
            val viewModel = start()

            val always = listOf(SetupMenuOption.EDIT, SetupMenuOption.VIEW_CONFIGURATION, SetupMenuOption.DUPLICATE, SetupMenuOption.MOVE_TO_GROUP, SetupMenuOption.DELETE)
            assertEquals(always, viewModel.section("plain").menuOptions)
            assertEquals(
                always + listOf(SetupMenuOption.RESTART_FAILED, SetupMenuOption.CONTACT_SUPPORT),
                viewModel.section("failing").menuOptions.sortedBy { it !in always },
            )
        }

    @Test
    fun `restart failed and contact support from a setup's menu act on that setup`() =
        runTest {
            val failing = world.setup("failing", null, errored(), supportUrl = "https://example.com/support")
            world.setup("other", null, errored(), supportUrl = "https://example.com/other")
            val viewModel = start()

            viewModel.onSetupMenuOptionSelected(failing.id, SetupMenuOption.RESTART_FAILED)
            viewModel.onSetupMenuOptionSelected(failing.id, SetupMenuOption.CONTACT_SUPPORT)
            settle()

            // The test script ends at once, so a restarted task shows up as a fresh Running entry in its history.
            assertTrue(world.wasRestarted("failing-task-1"))
            assertFalse(world.wasRestarted("other-task-1"))
            assertEquals(listOf("https://example.com/support"), world.systemProvider.browsedUrls)
        }

    @Test
    fun `rename from a group label's menu opens the group dialog, and saving renames the label`() =
        runTest {
            val work = world.group("Work")
            world.setup("a", work, idle())
            val viewModel = start()

            viewModel.onGroupMenuOptionSelected(work.id, GroupMenuOption.RENAME)
            val dialog = assertIs<TaskViewState.DialogState.EditingTaskGroup>(viewModel.dialogState.value)
            viewModel.editGroup(dialog.group, "Drops")
            settle()

            assertEquals(listOf("Default", "Drops"), viewModel.groups().map { it.name })
            assertEquals(TaskViewState.DialogState.Hidden, viewModel.dialogState.value)
        }

    @Test
    fun `new task setup from a group label's menu opens the new-setup dialog for that group`() =
        runTest {
            val work = world.group("Work")
            world.setup("a", work, idle())
            val viewModel = start()

            viewModel.onGroupMenuOptionSelected(work.id, GroupMenuOption.NEW_TASK_SETUP)

            val dialog = assertIs<TaskViewState.DialogState.SelectingScript>(viewModel.dialogState.value)
            assertEquals(work.id, dialog.scriptPackageGroup.id)
        }

    @Test
    fun `delete from a group label's menu asks for confirmation while it holds setups, then removes it`() =
        runTest {
            val work = world.group("Work")
            world.setup("a", work, idle())
            val viewModel = start()

            viewModel.onGroupMenuOptionSelected(work.id, GroupMenuOption.DELETE)
            val dialog = assertIs<TaskViewState.DialogState.ConfirmGroupDeletion>(viewModel.dialogState.value)
            assertEquals(listOf("Default", "Work"), viewModel.groups().map { it.name })

            viewModel.confirmGroupDeletion(dialog.group)
            settle()

            assertIs<TaskViewState.ListState.Empty>(viewModel.listState.value)
        }

    @Test
    fun `delete from a group label's menu removes an empty group at once`() =
        runTest {
            val work = world.group("Work")
            world.group("Drops")
            val viewModel = start()

            viewModel.onGroupMenuOptionSelected(work.id, GroupMenuOption.DELETE)
            settle()

            assertEquals(listOf("Default", "Drops"), viewModel.groups().map { it.name })
        }

    @Test
    fun `a newly created task setup appears in the list and none of its tasks start`() =
        runTest {
            world.setup("a", null, idle())
            val viewModel = start()

            world.setup("new", null, idle(), idle())
            settle()

            // Newest first by default.
            assertEquals(listOf("new", "a"), viewModel.groups().flatMap { it.setups }.map { it.setupId })
            assertEquals(Triple(0, 0, 2), viewModel.section("new").counts())
        }

    @Test
    fun `deleting a task setup removes its section`() =
        runTest {
            val a = world.setup("a", null, idle())
            world.setup("b", null, idle())
            val viewModel = start()

            viewModel.confirmScriptInstanceDeletion(a)
            settle()

            assertEquals(listOf("b"), viewModel.groups().flatMap { it.setups }.map { it.setupId })
        }

    @Test
    fun `focusing a task selects it and asks the list to scroll to it`() =
        runTest {
            world.setup("a", null, idle())
            world.setup("b", null, idle(), errored())
            val viewModel = world.viewModel().also { viewModel = it }

            viewModel.focusTask("b-task-2")
            settle()

            assertEquals("b-task-2", viewModel.selectedTask.value?.id)
            assertEquals("b-task-2", viewModel.scrollToTaskId.value)

            viewModel.onScrolledToTask()
            assertEquals(null, viewModel.scrollToTaskId.value)
        }
}

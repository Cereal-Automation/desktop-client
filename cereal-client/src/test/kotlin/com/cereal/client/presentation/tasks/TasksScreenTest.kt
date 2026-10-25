package com.cereal.client.presentation.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.TasksRepository
import fixtures.aScriptInstance
import fixtures.aScriptPackageInstance
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/** Renders [TasksScreen] on in-memory repositories. */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class, ExperimentalTime::class)
class TasksScreenTest {
    @Test
    fun rendersTasksPaneWithEmptyState() =
        runScreenTest {
            setScreenContent { TasksScreen() }

            // The list state is resolved asynchronously from flows, so wait for it to settle.
            waitUntil { onAllNodesWithText("No task setups yet").fetchSemanticsNodes().isNotEmpty() }

            onAllNodesWithText("Tasks")[0].assertIsDisplayed()
            onNodeWithText("·  0 setups · 0 tasks").assertIsDisplayed()
            onNodeWithText("New group").assertIsDisplayed()
            onAllNodesWithText("New task setup")[0].assertIsDisplayed()
            onNodeWithText("or describe a goal on Home").assertIsDisplayed()
        }

    @Test
    fun rendersASeededTaskSetupAsASectionWithItsRows() =
        runScreenTest {
            seedOneSetupWithTwoTasks()

            setScreenContent { TasksScreen() }
            waitUntil { onAllNodesWithText("Watching stock").fetchSemanticsNodes().isNotEmpty() }

            onNodeWithText("·  1 setup · 2 tasks").assertIsDisplayed()
            onNodeWithText("DEFAULT · 1").assertIsDisplayed()
            onNodeWithText("Test").assertIsDisplayed()
            onNodeWithText("1 running").assertIsDisplayed()
            onNodeWithText("1 failed").assertIsDisplayed()
            onNodeWithText("Watching stock").assertIsDisplayed()
            onNodeWithText("Sold out").assertIsDisplayed()
            // No bottom Output panel; a task's output lives in the inspector, which is closed until a row is clicked.
            onNodeWithText("Output").assertDoesNotExist()
            onNodeWithText("Task #2").assertDoesNotExist()
        }

    @Test
    fun clickingARowOpensTheInspectorAndClickingItAgainClosesIt() =
        runScreenTest {
            seedOneSetupWithTwoTasks()
            setScreenContent { TasksScreen() }
            waitUntil { onAllNodesWithText("Sold out").fetchSemanticsNodes().isNotEmpty() }

            onNodeWithText("Sold out").performClick()
            waitUntil { onAllNodesWithText("Task #2").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("LATEST MESSAGE").assertIsDisplayed()
            onNodeWithText("Restart").assertIsDisplayed()
            onNodeWithText("LOG").assertIsDisplayed()
            onNodeWithText("None yet").assertExists()

            onAllNodesWithText("Sold out")[0].performClick()
            waitUntil { onAllNodesWithText("Task #2").fetchSemanticsNodes().isEmpty() }
        }

    @Test
    fun closingTheInspectorHidesIt() =
        runScreenTest {
            seedOneSetupWithTwoTasks()
            setScreenContent { TasksScreen() }
            waitUntil { onAllNodesWithText("Watching stock").fetchSemanticsNodes().isNotEmpty() }

            onNodeWithText("Watching stock").performClick()
            waitUntil { onAllNodesWithText("Task #1").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Stop").assertIsDisplayed()

            onNodeWithContentDescription("Close").performClick()
            waitUntil { onAllNodesWithText("Task #1").fetchSemanticsNodes().isEmpty() }
        }

    @Test
    fun clickingAnywhereOnASetupRowCollapsesAndExpandsIt() =
        runScreenTest {
            seedOneSetupWithTwoTasks()
            setScreenContent { TasksScreen() }
            waitUntil { onAllNodesWithText("Sold out").fetchSemanticsNodes().isNotEmpty() }

            // The task count is plain text on the row, away from the chevron and the name.
            onNodeWithText("2 tasks").performClick()
            waitUntil { onAllNodesWithText("Sold out").fetchSemanticsNodes().isEmpty() }

            onNodeWithText("2 tasks").performClick()
            waitUntil { onAllNodesWithText("Sold out").fetchSemanticsNodes().isNotEmpty() }
        }

    @Test
    fun theInspectorTakesAThirdOfTheWidthButNoLessThanItsMinimum() {
        assertEquals(500.dp, inspectorWidthWhenScreenIs(1500.dp))
        assertEquals(InspectorMinWidth, inspectorWidthWhenScreenIs(900.dp))
    }

    private fun inspectorWidthWhenScreenIs(screenWidth: Dp): Dp {
        var width = 0.dp
        runScreenTest {
            seedOneSetupWithTwoTasks()
            setScreenContent { Box(Modifier.requiredWidth(screenWidth)) { TasksScreen() } }
            waitUntil { onAllNodesWithText("Sold out").fetchSemanticsNodes().isNotEmpty() }

            onNodeWithText("Sold out").performClick()
            waitUntil { onAllNodesWithTag(TASK_INSPECTOR_TEST_TAG).fetchSemanticsNodes().isNotEmpty() }
            width = onNodeWithTag(TASK_INSPECTOR_TEST_TAG).getUnclippedBoundsInRoot().width
        }
        return width
    }

    @Test
    fun aSetupsEditEntryIsDisabledWhileItsTasksRun() =
        runScreenTest {
            seedOneSetupWithTwoTasks()
            setScreenContent { TasksScreen() }
            waitUntil { onAllNodesWithText("Watching stock").fetchSemanticsNodes().isNotEmpty() }

            // The group label's "···" comes first, then the setup's.
            onAllNodesWithContentDescription("More options")[1].performClick()
            waitUntil { onAllNodesWithText("Edit").fetchSemanticsNodes().isNotEmpty() }

            onNodeWithText("Edit").assertIsNotEnabled()
            onNodeWithText("View full configuration").assertIsEnabled()
        }

    @Test
    fun aGroupLabelsMenuOffersRenameAndDelete() =
        runScreenTest {
            seedOneSetupWithTwoTasks()
            setScreenContent { TasksScreen() }
            waitUntil { onAllNodesWithText("DEFAULT · 1").fetchSemanticsNodes().isNotEmpty() }

            onAllNodesWithContentDescription("More options")[0].performClick()
            waitUntil { onAllNodesWithText("Rename").fetchSemanticsNodes().isNotEmpty() }

            onNodeWithText("Delete").assertIsDisplayed()
        }

    /** One setup in the default group: task 1 running ("Watching stock"), task 2 failed ("Sold out"). */
    private fun seedOneSetupWithTwoTasks() =
        runBlocking {
            val group = KoinJavaComponent.get<TasksRepository>(TasksRepository::class.java).createScriptInstanceGroup(ScriptPackageGroup.createDefault())
            val setup = aScriptPackageInstance("setup-1", "com.example.monitor")
            val main = aScriptInstance(setup)
            KoinJavaComponent.get<ScriptInstanceRepository>(ScriptInstanceRepository::class.java).addScriptPackageInstance(group.id, setup, main)
            val now = Clock.System.now()
            KoinJavaComponent.get<TasksRepository>(TasksRepository::class.java).addAllTasks(
                listOf("Watching stock", "Sold out").mapIndexed { index, message ->
                    JobTask(
                        id = "task-${index + 1}",
                        scriptInstance = main,
                        configuration = emptyMap(),
                        statusHistory = listOf(if (index == 0) TaskStatus.Running(message, now) else TaskStatus.Error(message, timestamp = now)),
                        createdAt = now + index.milliseconds,
                    )
                },
            )
        }
}

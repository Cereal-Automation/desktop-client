package com.cereal.client.presentation.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cereal.client.presentation.tasks.mappers.toUiModel
import com.cereal.client.presentation.tasks.model.TaskUiModel
import fixtures.aTask
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Renders [TaskRow] in isolation with directly-constructed [TaskUiModel] values and asserts both the
 * displayed content and that the start/stop callbacks fire on click.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class, ExperimentalTime::class)
class TaskRowTest {
    @Test
    fun rendersTaskContent() =
        runScreenTest {
            setScreenContent {
                TaskRow(
                    taskItem = taskUiModel(taskNumber = 7, message = "Scrape products", configuration = "config.json"),
                    isSelected = false,
                    onStartTask = {},
                    onStopTask = {},
                    onRowClick = {},
                )
            }

            onNodeWithText("#7").assertIsDisplayed()
            onNodeWithText("Scrape products").assertIsDisplayed()
            onNodeWithText("config.json").assertIsDisplayed()
        }

    @Test
    fun idleTaskReadsReady() =
        runScreenTest {
            setScreenContent {
                TaskRow(
                    taskItem = aTask(id = "task-1", packageName = "com.example.script", running = false).toUiModel(taskNumber = 1),
                    isSelected = false,
                    onStartTask = {},
                    onStopTask = {},
                    onRowClick = {},
                )
            }

            onNodeWithText("Ready").assertIsDisplayed()
        }

    @Test
    fun showsTimeSinceLastStatusChange() =
        runScreenTest {
            setScreenContent {
                TaskRow(
                    taskItem = taskUiModel(statusSince = Clock.System.now() - 5.minutes),
                    isSelected = false,
                    onStartTask = {},
                    onStopTask = {},
                    onRowClick = {},
                )
            }

            onNodeWithText("5m ago").assertIsDisplayed()
        }

    @Test
    fun showsNoTimeWithoutAStatusTimestamp() =
        runScreenTest {
            setScreenContent {
                TaskRow(
                    taskItem = taskUiModel(statusSince = null),
                    isSelected = false,
                    onStartTask = {},
                    onStopTask = {},
                    onRowClick = {},
                )
            }

            onAllNodesWithText("ago", substring = true).assertCountEquals(0)
        }

    @Test
    fun clickingStartFiresCallback() =
        runScreenTest {
            var started = false
            setScreenContent {
                TaskRow(
                    taskItem = taskUiModel(isRunning = false),
                    isSelected = false,
                    onStartTask = { started = true },
                    onStopTask = {},
                    onRowClick = {},
                )
            }

            onNodeWithContentDescription("Start task").performClick()
            assertTrue(started)
        }

    @Test
    fun clickingStopFiresCallback() =
        runScreenTest {
            var stopped = false
            setScreenContent {
                TaskRow(
                    taskItem = taskUiModel(isRunning = true, status = "Running"),
                    isSelected = false,
                    onStartTask = {},
                    onStopTask = { stopped = true },
                    onRowClick = {},
                )
            }

            onNodeWithContentDescription("Stop task").performClick()
            assertTrue(stopped)
        }

    @Test
    fun clickingRowFiresCallback() =
        runScreenTest {
            var clicked = false
            setScreenContent {
                TaskRow(
                    taskItem = taskUiModel(),
                    isSelected = false,
                    onStartTask = {},
                    onStopTask = {},
                    onRowClick = { clicked = true },
                )
            }

            onNodeWithText("#1").performClick()
            assertTrue(clicked)
        }

    @Test
    fun erroredTaskShowsReportIssueAndFiresCallback() =
        runScreenTest {
            var reported = false
            setScreenContent {
                TaskRow(
                    taskItem = taskUiModel(isError = true, status = "Errored"),
                    isSelected = false,
                    onStartTask = {},
                    onStopTask = {},
                    onRowClick = {},
                    onReportIssue = { reported = true },
                )
            }

            onNodeWithText("Report Issue").assertIsDisplayed()
            onNodeWithText("Report Issue").performClick()
            assertTrue(reported)
        }
}

private fun taskUiModel(
    taskNumber: Int = 1,
    isRunning: Boolean = false,
    isError: Boolean = false,
    isSuccess: Boolean = false,
    message: String = "Task message",
    status: String = "Idle",
    configuration: String = "",
    statusSince: Instant? = null,
): TaskUiModel =
    TaskUiModel(
        id = aTask(id = "task-$taskNumber", packageName = "com.example.script", running = isRunning),
        taskNumber = taskNumber,
        isRunning = isRunning,
        isError = isError,
        isSuccess = isSuccess,
        hasSupportUrl = false,
        message = message,
        status = status,
        configuration = configuration,
        stackTrace = null,
        statusSince = statusSince,
    )

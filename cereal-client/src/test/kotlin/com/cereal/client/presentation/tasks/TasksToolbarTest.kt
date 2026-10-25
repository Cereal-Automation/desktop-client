package com.cereal.client.presentation.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renders [TasksToolbar] and [TasksQuickActions] in isolation: title/subtitle, the view-config and
 * contact-support callbacks, the status chips, the Start all / Stop all toggle and Restart errored.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class TasksToolbarTest {
    @Test
    fun rendersTitleAndSubtitle() =
        runScreenTest {
            setScreenContent {
                TasksToolbar(
                    title = "Tasks",
                    subtitle = "12 active",
                )
            }

            onNodeWithText("Tasks").assertIsDisplayed()
            onNodeWithText("·  12 active").assertIsDisplayed()
        }

    @Test
    fun clickingViewConfigFiresCallback() =
        runScreenTest {
            var viewed = false
            setScreenContent {
                TasksToolbar(
                    title = "Tasks",
                    subtitle = null,
                    onViewConfig = { viewed = true },
                )
            }

            onNodeWithText("View config").assertIsDisplayed()
            onNodeWithText("View config").performClick()
            assertTrue(viewed)
        }

    @Test
    fun moreOptionsMenuFiresSelectedOption() =
        runScreenTest {
            var selected: MenuOption? = null
            setScreenContent {
                TasksToolbar(
                    title = "My setup",
                    subtitle = null,
                    menuOptions = listOf(MenuOption.DUPLICATE, MenuOption.MOVE_TO_GROUP, MenuOption.DELETE),
                    onMenuOptionSelected = { selected = it },
                )
            }

            onNodeWithContentDescription("More options").performClick()
            onNodeWithText("Delete").performClick()
            assertEquals(MenuOption.DELETE, selected)
        }

    @Test
    fun clickingContactSupportFiresCallback() =
        runScreenTest {
            var contacted = false
            setScreenContent {
                TasksToolbar(
                    title = "Tasks",
                    subtitle = null,
                    onContactSupport = { contacted = true },
                )
            }

            onNodeWithText("Contact support").assertIsDisplayed()
            onNodeWithText("Contact support").performClick()
            assertTrue(contacted)
        }

    @Test
    fun rendersStatusChipsAndClickingOneReportsItsFilter() =
        runScreenTest {
            var clicked: TaskFilter? = null
            setScreenContent {
                TasksQuickActions(quickActionsState(), callbacks(onStatusChipClick = { clicked = it }))
            }

            onNodeWithText("Idle 1").assertIsDisplayed()
            onNodeWithText("Running 0").assertIsDisplayed()
            onNodeWithText("Success 0").assertIsDisplayed()
            onNodeWithText("Errored 0").performClick()
            assertEquals(TaskFilter.ERROR, clicked)
        }

    @Test
    fun toggleShowsStartAllWhenNothingRuns() =
        runScreenTest {
            var started = false
            setScreenContent {
                TasksQuickActions(quickActionsState(showStopAll = false), callbacks(onStartAll = { started = true }))
            }

            onNodeWithText("Stop all").assertDoesNotExist()
            onNodeWithText("Start all").performClick()
            assertTrue(started)
        }

    @Test
    fun startAllIsHiddenWhenThereIsNothingToStart() =
        runScreenTest {
            setScreenContent {
                TasksQuickActions(quickActionsState().copy(showStartAll = false), callbacks())
            }

            onNodeWithText("Start all").assertDoesNotExist()
            onNodeWithText("Stop all").assertDoesNotExist()
        }

    @Test
    fun toggleShowsStopAllWhileTasksRun() =
        runScreenTest {
            var stopped = false
            setScreenContent {
                TasksQuickActions(quickActionsState(showStopAll = true), callbacks(onStopAll = { stopped = true }))
            }

            onNodeWithText("Start all").assertDoesNotExist()
            onNodeWithText("Stop all").performClick()
            assertTrue(stopped)
        }

    @Test
    fun restartErroredOnlyRendersWhenShown() =
        runScreenTest {
            var showRestart by mutableStateOf(false)
            setScreenContent {
                TasksQuickActions(quickActionsState(showRestartErrored = showRestart), callbacks())
            }

            onNodeWithText("Restart errored").assertDoesNotExist()
            showRestart = true
            onNodeWithText("Restart errored").assertIsDisplayed()
        }

    private fun quickActionsState(
        showStopAll: Boolean = false,
        showRestartErrored: Boolean = false,
    ) = TaskQuickActionsState(
        counts = TaskCounts(idle = 1, running = 0, finished = 0, errored = 0),
        filter = TaskFilter.ALL,
        showStopAll = showStopAll,
        showStartAll = !showStopAll,
        showRestartErrored = showRestartErrored,
        restartErroredEnabled = true,
    )

    private fun callbacks(
        onStatusChipClick: (TaskFilter) -> Unit = {},
        onStartAll: () -> Unit = {},
        onStopAll: () -> Unit = {},
    ) = TaskQuickActionsCallbacks(onStatusChipClick, onStartAll, onStopAll, onRestartErrored = {})
}

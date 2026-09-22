package com.cereal.client.presentation.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders [TasksToolbar] in isolation and asserts the title/subtitle render and that the
 * view-config and contact-support callbacks fire on click.
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
}

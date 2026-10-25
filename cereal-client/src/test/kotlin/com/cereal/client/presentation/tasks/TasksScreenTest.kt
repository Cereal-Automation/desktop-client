package com.cereal.client.presentation.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test

/**
 * Renders [TasksScreen] on in-memory repositories. Nothing is seeded, so the screen settles into its
 * empty state under the "Tasks" pane header.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class TasksScreenTest {
    @Test
    fun rendersTasksPaneWithEmptyState() =
        runScreenTest {
            setScreenContent { TasksScreen() }

            // The list state is resolved asynchronously from flows, so wait for it to settle.
            waitUntil { onAllNodesWithText("No task setups yet").fetchSemanticsNodes().isNotEmpty() }

            onAllNodesWithText("Tasks")[0].assertIsDisplayed()
            onNodeWithText("0 setups · 0 running").assertIsDisplayed()
            onNodeWithContentDescription("New task setup").assertIsDisplayed()
            onNodeWithText("New task setup").assertIsDisplayed()
            onNodeWithText("or describe a goal on Home").assertIsDisplayed()
            onNodeWithText("Create group").assertDoesNotExist()
        }
}

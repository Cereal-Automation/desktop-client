package com.cereal.client.presentation.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test

/**
 * Renders [TasksScreen] on in-memory repositories. The harness seeds a default task group, so the
 * screen settles into its filled state: the "Tasks" toolbar title alongside the "Scripts" column.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class TasksScreenTest {
    @Test
    fun rendersToolbarAndScriptsColumn() =
        runScreenTest {
            setScreenContent { TasksScreen() }

            // The scripts column is populated asynchronously from a flow, so wait for it to settle.
            waitUntil { onAllNodesWithText("Scripts").fetchSemanticsNodes().isNotEmpty() }

            onNodeWithText("Tasks").assertIsDisplayed()
            onNodeWithText("Scripts").assertIsDisplayed()
        }
}

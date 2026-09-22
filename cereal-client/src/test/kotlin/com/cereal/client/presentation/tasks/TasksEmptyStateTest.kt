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
 * Renders [TasksEmptyState] in isolation and verifies both its static content and that its two
 * action buttons invoke the supplied callbacks.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class TasksEmptyStateTest {
    @Test
    fun rendersEmptyStateContent() =
        runScreenTest {
            setScreenContent {
                TasksEmptyState(onCreateGroup = {}, onAddScript = {})
            }

            onNodeWithText("No script groups yet").assertIsDisplayed()
            onNodeWithText("Create group").assertIsDisplayed()
            onNodeWithText("Add script").assertIsDisplayed()
        }

    @Test
    fun clickingCreateGroupFiresCallback() =
        runScreenTest {
            var createGroupClicked = false
            setScreenContent {
                TasksEmptyState(
                    onCreateGroup = { createGroupClicked = true },
                    onAddScript = {},
                )
            }

            onNodeWithText("Create group").performClick()

            assertTrue(createGroupClicked, "onCreateGroup should be invoked when Create group is clicked")
        }

    @Test
    fun clickingAddScriptFiresCallback() =
        runScreenTest {
            var addScriptClicked = false
            setScreenContent {
                TasksEmptyState(
                    onCreateGroup = {},
                    onAddScript = { addScriptClicked = true },
                )
            }

            onNodeWithText("Add script").performClick()

            assertTrue(addScriptClicked, "onAddScript should be invoked when Add script is clicked")
        }
}

package com.cereal.client.presentation.view.group

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Render + interaction tests for [ScriptsColumn]. Builds a [HierarchicalListViewModel] with one
 * expanded parent group containing a child, then asserts the "Tasks" header and child render (the only group's header doesn't),
 * and that "+" and the "···" menu's New group invoke their callbacks.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class ScriptsColumnUiTest {
    private fun createViewModel(): HierarchicalListViewModel<String, String> {
        val viewModel =
            HierarchicalListViewModel<String, String>(
                onParentSelectedChange = {},
                onChildSelectedChange = {},
            )
        viewModel.updateItems(
            parents = listOf(GroupListItemContent(id = "group-1", title = "My Group")),
            childrenByParent =
                mapOf(
                    "group-1" to listOf(GroupListItemContent(id = "script-1", title = "My Script")),
                ),
            selectFirstWhenNoSelection = true,
        )
        return viewModel
    }

    @Test
    fun rendersHeaderAndChildWithoutTheOnlyGroupsHeader() =
        runScreenTest {
            val viewModel = createViewModel()

            setScreenContent {
                ScriptsColumn(
                    viewModel = viewModel,
                    onNewTaskSetup = {},
                    onCreateGroup = {},
                )
            }

            onNodeWithText("Tasks").assertIsDisplayed()
            onNodeWithText("1 setup · 0 running").assertIsDisplayed()
            onNodeWithText("My Group").assertDoesNotExist()
            onNodeWithText("My Script").assertIsDisplayed()
            onNodeWithText("Add Script").assertDoesNotExist()
        }

    @Test
    fun clickingPlusFiresNewTaskSetup() =
        runScreenTest {
            var newTaskSetupClicks = 0

            setScreenContent {
                ScriptsColumn(
                    viewModel = createViewModel(),
                    onNewTaskSetup = { newTaskSetupClicks++ },
                    onCreateGroup = {},
                )
            }

            onNodeWithContentDescription("New task setup").performClick()

            assertEquals(1, newTaskSetupClicks)
        }

    @Test
    fun newGroupInOverflowMenuFiresCallback() =
        runScreenTest {
            var createGroupClicks = 0

            setScreenContent {
                ScriptsColumn(
                    viewModel = createViewModel(),
                    onNewTaskSetup = {},
                    onCreateGroup = { createGroupClicks++ },
                )
            }

            onNodeWithContentDescription("More options").performClick()
            onNodeWithText("New group").performClick()

            assertEquals(1, createGroupClicks)
        }
}

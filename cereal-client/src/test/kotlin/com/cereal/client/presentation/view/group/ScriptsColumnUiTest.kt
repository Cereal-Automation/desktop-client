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
 * expanded parent group containing a child, then asserts the header, group, child and the
 * "Add Script" affordance render, and that the "New group" button invokes [onCreateGroup].
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
    fun rendersHeaderGroupAndChild() =
        runScreenTest {
            val viewModel = createViewModel()

            setScreenContent {
                ScriptsColumn(
                    viewModel = viewModel,
                    onCreateGroup = {},
                )
            }

            onNodeWithText("Scripts").assertIsDisplayed()
            onNodeWithText("My Group").assertIsDisplayed()
            onNodeWithText("My Script").assertIsDisplayed()
            // The add-script affordance only shows when the parent is expanded.
            onNodeWithText("Add Script").assertIsDisplayed()
        }

    @Test
    fun clickingNewGroupFiresCallback() =
        runScreenTest {
            val viewModel = createViewModel()
            var createGroupClicks = 0

            setScreenContent {
                ScriptsColumn(
                    viewModel = viewModel,
                    onCreateGroup = { createGroupClicks++ },
                )
            }

            onNodeWithContentDescription("New group").performClick()

            assertEquals(1, createGroupClicks)
        }
}

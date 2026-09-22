package com.cereal.client.presentation.view.group

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Render + interaction tests for [DetailedListView]. Verifies rows render, and that the edit and
 * delete icon buttons (whose content descriptions are the "Edit" / "Delete" strings) fire their
 * callbacks with the clicked item.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class DetailedListViewUiTest {
    private val header = DetailListViewHeader(headers = listOf("Name", "Value"))
    private val firstItem = DetailListViewItem(id = "1", attributes = listOf("Alpha", "111"))
    private val secondItem = DetailListViewItem(id = "2", attributes = listOf("Beta", "222"))

    @Test
    fun rendersHeaderAndItems() =
        runScreenTest {
            setScreenContent {
                DetailedListView(
                    header = header,
                    items = listOf(firstItem, secondItem),
                    onEditListItem = {},
                    onDeleteListItem = {},
                )
            }

            onNodeWithText("Name").assertIsDisplayed()
            onNodeWithText("Value").assertIsDisplayed()
            onNodeWithText("Alpha").assertIsDisplayed()
            onNodeWithText("Beta").assertIsDisplayed()
        }

    @Test
    fun clickingEditFiresCallbackWithItem() =
        runScreenTest {
            val edited = mutableListOf<DetailListViewItem>()
            setScreenContent {
                DetailedListView(
                    header = header,
                    items = listOf(firstItem),
                    onEditListItem = { edited.add(it) },
                    onDeleteListItem = {},
                )
            }

            onAllNodesWithContentDescription("Edit").onFirst().performClick()

            assertEquals(listOf(firstItem), edited)
        }

    @Test
    fun clickingDeleteFiresCallbackWithItem() =
        runScreenTest {
            val deleted = mutableListOf<DetailListViewItem>()
            setScreenContent {
                DetailedListView(
                    header = header,
                    items = listOf(firstItem),
                    onEditListItem = {},
                    onDeleteListItem = { deleted.add(it) },
                )
            }

            onAllNodesWithContentDescription("Delete").onFirst().performClick()

            assertEquals(listOf(firstItem), deleted)
        }
}

package com.cereal.client.presentation.view.group

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Render test for [GroupedListView]. Builds a [GroupedListViewModel] with simple string-id data,
 * pushes items into it, and asserts the rendered list shows each item's title.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class GroupedListViewUiTest {
    private fun content(
        id: String,
        title: String,
    ) = GroupListItemContent(id = id, title = title)

    @Test
    fun rendersItemTitles() =
        runScreenTest {
            val viewModel =
                GroupedListViewModel<String>(
                    onSelectedItemChange = {},
                )
            viewModel.updateItems(
                listOf(content("a", "First script"), content("b", "Second script")),
                selectFirstWhenNoSelection = false,
            )

            setScreenContent { GroupedListView(viewModel = viewModel) }

            onNodeWithText("First script").assertIsDisplayed()
            onNodeWithText("Second script").assertIsDisplayed()
        }

    @Test
    fun rendersSubtitleAndBadge() =
        runScreenTest {
            val viewModel =
                GroupedListViewModel<String>(
                    onSelectedItemChange = {},
                )
            viewModel.updateItems(
                listOf(
                    GroupListItemContent(
                        id = "a",
                        title = "Titled item",
                        subTitle = "A subtitle",
                        badge = "9",
                    ),
                ),
                selectFirstWhenNoSelection = false,
            )

            setScreenContent { GroupedListView(viewModel = viewModel) }

            onNodeWithText("Titled item").assertIsDisplayed()
            onNodeWithText("A subtitle").assertIsDisplayed()
            onNodeWithText("9").assertIsDisplayed()
        }

    @Test
    fun enterOnFocusedRowSelectsIt() =
        runScreenTest {
            var selected: String? = null
            val viewModel =
                GroupedListViewModel<String>(
                    onSelectedItemChange = { selected = it },
                )
            viewModel.updateItems(
                listOf(content("a", "First script"), content("b", "Second script")),
                selectFirstWhenNoSelection = false,
            )

            setScreenContent { GroupedListView(viewModel = viewModel) }

            onNode(hasText("Second script") and hasClickAction()).apply {
                requestFocus()
                performKeyInput { pressKey(Key.Enter) }
            }
            waitForIdle()

            assertEquals("b", selected)
        }
}

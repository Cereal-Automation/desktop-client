package com.cereal.client.presentation.view.group

import com.cereal.client.presentation.tasks.MenuOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroupedListViewModelTest {
    private val selectionChanges = mutableListOf<String?>()
    private val menuSelections = mutableListOf<Pair<String, MenuOption>>()

    private fun createViewModel(
        menuOptions: List<MenuOption> = listOf(MenuOption.DELETE),
    ) = GroupedListViewModel<String>(
        menuOptions = menuOptions,
        onSelectedItemChange = { selectionChanges.add(it) },
        onMenuOptionSelected = { item, option -> menuSelections.add(item to option) },
    )

    private fun content(id: String) = GroupListItemContent(id = id, title = "Title $id")

    @Test
    fun `updateItems with selectFirstWhenNoSelection selects the first item`() {
        val viewModel = createViewModel()

        viewModel.updateItems(listOf(content("a"), content("b")), selectFirstWhenNoSelection = true)

        assertEquals("a", viewModel.selectedItem)
        assertEquals(2, viewModel.state.value.size)
        assertTrue(
            viewModel.state.value
                .first { it.content.id == "a" }
                .selected,
        )
        assertFalse(
            viewModel.state.value
                .first { it.content.id == "b" }
                .selected,
        )
        assertEquals(listOf("a"), selectionChanges)
    }

    @Test
    fun `updateItems without selectFirst keeps no selection`() {
        val viewModel = createViewModel()

        viewModel.updateItems(listOf(content("a")), selectFirstWhenNoSelection = false)

        assertNull(viewModel.selectedItem)
        assertTrue(selectionChanges.isEmpty())
    }

    @Test
    fun `updateItems preserves existing selection across updates`() {
        val viewModel = createViewModel()
        viewModel.selectItem("a")
        selectionChanges.clear()

        viewModel.updateItems(listOf(content("a"), content("b")), selectFirstWhenNoSelection = true)

        assertEquals("a", viewModel.selectedItem)
        // No new selection callback because selection did not change.
        assertTrue(selectionChanges.isEmpty())
    }

    @Test
    fun `onItemClick selects the clicked item`() {
        val viewModel = createViewModel()
        viewModel.updateItems(listOf(content("a"), content("b")), selectFirstWhenNoSelection = false)

        viewModel.onItemClick(GroupListItem(content("b"), selected = false))

        assertEquals("b", viewModel.selectedItem)
        assertEquals(listOf("b"), selectionChanges)
    }

    @Test
    fun `selectItem with same id does not re-notify`() {
        val viewModel = createViewModel()
        viewModel.selectItem("a")
        selectionChanges.clear()

        viewModel.selectItem("a")

        assertTrue(selectionChanges.isEmpty())
    }

    @Test
    fun `deselect clears selection and notifies`() {
        val viewModel = createViewModel()
        viewModel.selectItem("a")
        selectionChanges.clear()

        viewModel.deselect()

        assertNull(viewModel.selectedItem)
        assertEquals(listOf<String?>(null), selectionChanges)
    }

    @Test
    fun `onMenuOptionClick forwards to the menu callback`() {
        val viewModel = createViewModel()

        viewModel.onMenuOptionClick(GroupListItem(content("a"), selected = false), MenuOption.DELETE)

        assertEquals(listOf("a" to MenuOption.DELETE), menuSelections)
    }

    @Test
    fun `menuOptions are exposed`() {
        val viewModel = createViewModel(menuOptions = listOf(MenuOption.EDIT, MenuOption.DELETE))

        assertEquals(listOf(MenuOption.EDIT, MenuOption.DELETE), viewModel.menuOptions)
    }
}

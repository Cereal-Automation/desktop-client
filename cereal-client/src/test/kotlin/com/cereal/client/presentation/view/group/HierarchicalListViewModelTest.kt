package com.cereal.client.presentation.view.group

import com.cereal.client.presentation.tasks.MenuOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HierarchicalListViewModelTest {
    private val parentSelections = mutableListOf<String?>()
    private val childSelections = mutableListOf<String?>()
    private val parentMenu = mutableListOf<Pair<String, MenuOption>>()
    private val childMenu = mutableListOf<Pair<String, MenuOption>>()
    private val addChildClicks = mutableListOf<String>()

    private fun createViewModel() =
        HierarchicalListViewModel<String, String>(
            parentMenuOptions = listOf(MenuOption.EDIT),
            childMenuOptions = listOf(MenuOption.DELETE),
            onParentSelectedChange = { parentSelections.add(it) },
            onChildSelectedChange = { childSelections.add(it) },
            onParentMenuOptionSelected = { item, option -> parentMenu.add(item to option) },
            onChildMenuOptionSelected = { item, option -> childMenu.add(item to option) },
            onAddChildClicked = { addChildClicks.add(it) },
        )

    private fun content(id: String) = GroupListItemContent(id = id, title = "Title $id")

    private fun sampleData() =
        Pair(
            listOf(content("p1"), content("p2")),
            mapOf(
                "p1" to listOf(content("c1"), content("c2")),
                "p2" to listOf(content("c3")),
            ),
        )

    private fun parents(vm: HierarchicalListViewModel<String, String>) = vm.state.value.filterIsInstance<HierarchicalListItem.Parent<String, String>>()

    private fun children(vm: HierarchicalListViewModel<String, String>) = vm.state.value.filterIsInstance<HierarchicalListItem.Child<String, String>>()

    @Test
    fun `updateItems with selectFirst selects and expands the first parent`() {
        val viewModel = createViewModel()
        val (p, c) = sampleData()

        viewModel.updateItems(p, c, selectFirstWhenNoSelection = true)

        assertEquals("p1", viewModel.selectedParent)
        assertEquals(listOf("p1"), parentSelections)
        // Nothing is collapsed by default, so every parent's children are present.
        assertEquals(listOf("c1", "c2", "c3"), children(viewModel).map { it.content.id })
        assertEquals(2, parents(viewModel).size)
    }

    @Test
    fun `toggleExpansion collapses and re-expands a parent`() {
        val viewModel = createViewModel()
        val (p, c) = sampleData()
        viewModel.updateItems(p, c, selectFirstWhenNoSelection = true)

        viewModel.toggleExpansion("p1")
        assertTrue(children(viewModel).none { it.parentId == "p1" })

        viewModel.toggleExpansion("p1")
        assertEquals(listOf("c1", "c2"), children(viewModel).filter { it.parentId == "p1" }.map { it.content.id })
    }

    @Test
    fun `onParentClick and onParentExpandClick toggle expansion`() {
        val viewModel = createViewModel()
        val (p, c) = sampleData()
        viewModel.updateItems(p, c, selectFirstWhenNoSelection = false)

        val parentItem = parents(viewModel).first { it.content.id == "p1" }
        viewModel.onParentClick(parentItem)
        assertTrue(children(viewModel).none { it.parentId == "p1" })

        viewModel.onParentExpandClick(parents(viewModel).first { it.content.id == "p1" })
        assertTrue(children(viewModel).any { it.parentId == "p1" })
    }

    @Test
    fun `selectParent clears child selection and notifies`() {
        val viewModel = createViewModel()
        val (p, c) = sampleData()
        viewModel.updateItems(p, c, selectFirstWhenNoSelection = false)

        viewModel.selectParent("p2")

        assertEquals("p2", viewModel.selectedParent)
        assertNull(viewModel.selectedChild)
        assertEquals(listOf("p2"), parentSelections)
        assertEquals(listOf<String?>(null), childSelections)
    }

    @Test
    fun `selectParent with same id does not re-notify`() {
        val viewModel = createViewModel()
        viewModel.selectParent("p1")
        parentSelections.clear()
        childSelections.clear()

        viewModel.selectParent("p1")

        assertTrue(parentSelections.isEmpty())
        assertTrue(childSelections.isEmpty())
    }

    @Test
    fun `selectChild also updates the selected parent`() {
        val viewModel = createViewModel()
        val (p, c) = sampleData()
        viewModel.updateItems(p, c, selectFirstWhenNoSelection = false)

        viewModel.selectChild("c3")

        assertEquals("c3", viewModel.selectedChild)
        assertEquals("p2", viewModel.selectedParent)
        assertEquals(listOf("c3"), childSelections)
    }

    @Test
    fun `onChildClick selects the child`() {
        val viewModel = createViewModel()
        val (p, c) = sampleData()
        viewModel.updateItems(p, c, selectFirstWhenNoSelection = false)

        viewModel.onChildClick(HierarchicalListItem.Child(content("c1"), parentId = "p1", selected = false))

        assertEquals("c1", viewModel.selectedChild)
    }

    @Test
    fun `updateItems auto-expands the parent of the selected child`() {
        val viewModel = createViewModel()
        val (p, c) = sampleData()
        viewModel.updateItems(p, c, selectFirstWhenNoSelection = false)
        viewModel.selectChild("c1")
        viewModel.toggleExpansion("p1") // collapse p1

        // Re-supplying items should re-expand the parent containing the selected child.
        viewModel.updateItems(p, c, selectFirstWhenNoSelection = false)

        assertTrue(children(viewModel).any { it.content.id == "c1" })
    }

    @Test
    fun `deselectAll clears selection and notifies both callbacks`() {
        val viewModel = createViewModel()
        val (p, c) = sampleData()
        viewModel.updateItems(p, c, selectFirstWhenNoSelection = false)
        viewModel.selectChild("c1")
        parentSelections.clear()
        childSelections.clear()

        viewModel.deselectAll()

        assertNull(viewModel.selectedParent)
        assertNull(viewModel.selectedChild)
        assertEquals(listOf<String?>(null), parentSelections)
        assertEquals(listOf<String?>(null), childSelections)
    }

    @Test
    fun `menu and add-child callbacks are forwarded`() {
        val viewModel = createViewModel()
        val parentItem = HierarchicalListItem.Parent<String, String>(content("p1"), emptyList(), expanded = true, selected = false)
        val childItem = HierarchicalListItem.Child(content("c1"), parentId = "p1", selected = false)

        viewModel.onParentMenuOptionClick(parentItem, MenuOption.EDIT)
        viewModel.onChildMenuOptionClick(childItem, MenuOption.DELETE)
        viewModel.onAddChildClick(parentItem)

        assertEquals(listOf("p1" to MenuOption.EDIT), parentMenu)
        assertEquals(listOf("c1" to MenuOption.DELETE), childMenu)
        assertEquals(listOf("p1"), addChildClicks)
    }

    @Test
    fun `parent items are never marked selected in the flattened state`() {
        val viewModel = createViewModel()
        val (p, c) = sampleData()
        viewModel.updateItems(p, c, selectFirstWhenNoSelection = true)

        assertTrue(parents(viewModel).none { it.selected })
    }

    @Test
    fun `menu option lists are exposed`() {
        val viewModel = createViewModel()

        assertEquals(listOf(MenuOption.EDIT), viewModel.parentMenuOptions)
        assertEquals(listOf(MenuOption.DELETE), viewModel.childMenuOptions)
    }
}

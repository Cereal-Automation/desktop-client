package com.cereal.client.presentation.view.group

import androidx.compose.runtime.mutableStateOf
import com.cereal.client.presentation.tasks.MenuOption

/**
 * ViewModel for managing a hierarchical list with parent-child relationships (e.g., groups and script instances)
 */
class HierarchicalListViewModel<P, C>(
    val parentMenuOptions: List<MenuOption> = emptyList(),
    val childMenuOptions: List<MenuOption> = emptyList(),
    private val onParentSelectedChange: (P?) -> Unit,
    private val onChildSelectedChange: (C?) -> Unit,
    private val onParentMenuOptionSelected: ((item: P, menuItem: MenuOption) -> Unit)? = null,
    private val onChildMenuOptionSelected: ((item: C, menuItem: MenuOption) -> Unit)? = null,
    private val onAddChildClicked: ((parentId: P) -> Unit)? = null,
) {
    val state = mutableStateOf<List<HierarchicalListItem<P, C>>>(emptyList())

    private var parents: List<GroupListItemContent<P>> = emptyList()
    private var childrenByParent: Map<P, List<GroupListItemContent<C>>> = emptyMap()
    private var collapsedParents = mutableSetOf<P>()

    var selectedParent: P? = null
        private set
    var selectedChild: C? = null
        private set

    fun updateItems(
        parents: List<GroupListItemContent<P>>,
        childrenByParent: Map<P, List<GroupListItemContent<C>>>,
        selectFirstWhenNoSelection: Boolean = false,
    ) {
        this.parents = parents
        this.childrenByParent = childrenByParent

        // Auto-expand parent if it has the selected child
        selectedChild?.let { selectedChildId ->
            val parentOfSelectedChild =
                childrenByParent.entries
                    .firstOrNull { (_, children) ->
                        children.any { it.id == selectedChildId }
                    }?.key

            parentOfSelectedChild?.let {
                collapsedParents.remove(it)
            }
        }

        if (selectFirstWhenNoSelection && selectedParent == null && selectedChild == null) {
            val firstParent = parents.firstOrNull()?.id
            if (firstParent != null) {
                selectParent(firstParent)
                // Auto-expand first parent
                collapsedParents.remove(firstParent)
            }
        }

        updateState()
    }

    fun onParentClick(parent: HierarchicalListItem.Parent<P, C>) {
        toggleExpansion(parent.content.id)
    }

    fun onParentExpandClick(parent: HierarchicalListItem.Parent<P, C>) {
        toggleExpansion(parent.content.id)
    }

    fun onChildClick(child: HierarchicalListItem.Child<P, C>) {
        selectChild(child.content.id)
    }

    fun onParentMenuOptionClick(
        parent: HierarchicalListItem.Parent<P, C>,
        option: MenuOption,
    ) {
        onParentMenuOptionSelected?.invoke(parent.content.id, option)
    }

    fun onChildMenuOptionClick(
        child: HierarchicalListItem.Child<P, C>,
        option: MenuOption,
    ) {
        onChildMenuOptionSelected?.invoke(child.content.id, option)
    }

    fun toggleExpansion(parentId: P) {
        if (collapsedParents.contains(parentId)) {
            collapsedParents.remove(parentId)
        } else {
            collapsedParents.add(parentId)
        }
        updateState()
    }

    fun selectParent(id: P?) {
        if (selectedParent != id) {
            selectedParent = id
            selectedChild = null
            updateState()

            onParentSelectedChange(id)
            onChildSelectedChange(null)
        }
    }

    fun selectChild(id: C?) {
        if (selectedChild != id) {
            selectedChild = id

            // Update selected parent to match the child's parent
            if (id != null) {
                val parentId =
                    childrenByParent.entries
                        .firstOrNull { (_, children) ->
                            children.any { it.id == id }
                        }?.key
                if (parentId != null) {
                    selectedParent = parentId
                }
            }

            updateState()

            onChildSelectedChange(id)
        }
    }

    fun deselectAll() {
        selectedParent = null
        selectedChild = null
        updateState()

        onParentSelectedChange(null)
        onChildSelectedChange(null)
    }

    private fun updateState() {
        val items = mutableListOf<HierarchicalListItem<P, C>>()

        for (parentContent in parents) {
            val parentId = parentContent.id
            val isExpanded = !collapsedParents.contains(parentId)
            val children = childrenByParent[parentId] ?: emptyList()

            val childItems =
                children.map { childContent ->
                    HierarchicalListItem.Child(
                        content = childContent,
                        parentId = parentId,
                        selected = childContent.id == selectedChild,
                    )
                }

            items.add(
                HierarchicalListItem.Parent(
                    content = parentContent,
                    children = childItems,
                    expanded = isExpanded,
                    selected = false,
                ),
            )

            // Add children if parent is expanded
            if (isExpanded) {
                items.addAll(childItems)
            }
        }

        state.value = items
    }

    fun onAddChildClick(parent: HierarchicalListItem.Parent<P, C>) {
        onAddChildClicked?.invoke(parent.content.id)
    }
}

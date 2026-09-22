package com.cereal.client.presentation.view.group

import androidx.compose.runtime.mutableStateOf
import com.cereal.client.presentation.tasks.MenuOption

class GroupedListViewModel<T>(
    val menuOptions: List<MenuOption> = emptyList(),
    private val onSelectedItemChange: (T?) -> Unit,
    private val onMenuOptionSelected: ((item: T, menuItem: MenuOption) -> Unit)? = null,
) {
    val state = mutableStateOf<List<GroupListItem<T>>>(emptyList())

    private var items: List<GroupListItemContent<T>> = emptyList()
    var selectedItem: T? = null
        private set

    fun updateItems(
        items: List<GroupListItemContent<T>>,
        selectFirstWhenNoSelection: Boolean,
    ) {
        this.items = items

        if (selectFirstWhenNoSelection && selectedItem == null) {
            selectItem(items.firstOrNull()?.id)
        } else {
            selectedItem = items.find { it.id == selectedItem }?.id ?: selectedItem
        }

        updateState()
    }

    fun onItemClick(item: GroupListItem<T>) {
        selectItem(item.content.id)
    }

    fun onMenuOptionClick(
        item: GroupListItem<T>,
        option: MenuOption,
    ) {
        onMenuOptionSelected?.invoke(item.content.id, option)
    }

    fun deselect() {
        selectItem(null)
    }

    fun selectItem(id: T?) {
        if (selectedItem != id) {
            selectedItem = id
            updateState()

            onSelectedItemChange(id)
        }
    }

    private fun updateState() {
        state.value =
            items.map {
                GroupListItem(it, it.id == selectedItem)
            }
    }
}

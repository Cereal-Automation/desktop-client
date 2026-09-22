package com.cereal.client.presentation.view.group

sealed class GroupViewState {
    data object Empty : GroupViewState()

    data object Loading : GroupViewState()

    class Filled(
        val viewModel: GroupedListViewModel<*>,
    ) : GroupViewState()
}

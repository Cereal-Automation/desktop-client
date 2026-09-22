package com.cereal.client.presentation.view.group

sealed class DetailViewState(
    val header: DetailListViewHeader? = null,
    val items: List<DetailListViewItem>,
    val title: String,
    val addButtonEnabled: Boolean = true,
    val editGroupEnabled: Boolean = true,
    val importButtonEnabled: Boolean = true,
    val deleteAllButtonEnabled: Boolean = true,
) {
    class NoSelection(
        headerTitle: String,
    ) : DetailViewState(
            addButtonEnabled = false,
            editGroupEnabled = false,
            importButtonEnabled = false,
            deleteAllButtonEnabled = false,
            items = emptyList(),
            title = headerTitle,
        )

    class Empty(
        headerTitle: String,
    ) : DetailViewState(
            items = emptyList(),
            title = headerTitle,
            deleteAllButtonEnabled = false,
        )

    class Loading(
        headerTitle: String,
    ) : DetailViewState(
            addButtonEnabled = false,
            editGroupEnabled = false,
            importButtonEnabled = false,
            deleteAllButtonEnabled = false,
            items = emptyList(),
            title = headerTitle,
        )

    class Filled(
        header: DetailListViewHeader,
        items: List<DetailListViewItem>,
        headerTitle: String,
    ) : DetailViewState(
            header = header,
            items = items,
            title = headerTitle,
        )
}

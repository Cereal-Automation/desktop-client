package com.cereal.client.presentation.view.group

data class GroupListItem<T>(
    val content: GroupListItemContent<T>,
    val selected: Boolean,
)

data class GroupListItemContent<T>(
    val id: T,
    val warningMessage: String? = null,
    val title: String,
    val subTitle: String? = null,
    val badge: String? = null,
    val isRunning: Boolean = false,
)

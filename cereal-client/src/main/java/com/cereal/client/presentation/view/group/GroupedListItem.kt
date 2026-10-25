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
    /** Status dot of the item; null shows none. */
    val status: ItemStatus? = null,
    /** Red marker: something in the item needs the user (an error or a pending interaction). */
    val needsAttention: Boolean = false,
)

enum class ItemStatus { IDLE, RUNNING, ERROR }

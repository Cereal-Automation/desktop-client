package com.cereal.client.application.task

/** A script-defined task action as shown in a task's menu; it is run by its index in the script's list. */
data class TaskActionItem(
    val name: String,
    val confirm: String?,
)

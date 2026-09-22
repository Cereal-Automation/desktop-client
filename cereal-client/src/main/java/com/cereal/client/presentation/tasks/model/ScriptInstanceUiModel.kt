package com.cereal.client.presentation.tasks.model

import com.cereal.client.domain.model.script.ScriptInstance

data class ScriptInstanceUiModel(
    val id: ScriptInstance,
    val title: String?,
    val tasks: MutableList<TaskUiModel> = mutableListOf(),
)

package com.cereal.client.presentation.tasks

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp

/** The inspector's dragged width. A Koin single, so it outlives the per-visit [TasksViewModel] until the app quits. */
class TaskInspectorLayoutState {
    private val _width = mutableStateOf<Dp?>(null)

    /** Null until the user drags the inspector; the screen then uses its default width. */
    val width: State<Dp?> = _width

    fun setWidth(width: Dp) {
        _width.value = width
    }
}

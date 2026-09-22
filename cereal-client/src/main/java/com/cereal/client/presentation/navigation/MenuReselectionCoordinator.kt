package com.cereal.client.presentation.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MenuReselectionCoordinator {
    private val _events = MutableSharedFlow<Root.Routing>(extraBufferCapacity = 1)
    val events: SharedFlow<Root.Routing> = _events.asSharedFlow()

    fun notifyReselected(route: Root.Routing) {
        _events.tryEmit(route)
    }
}

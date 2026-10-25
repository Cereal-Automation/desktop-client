package com.cereal.client.presentation.tasks

/**
 * The task setup the user last selected on the Tasks screen. A Koin single, so it outlives the per-visit
 * [TasksViewModel] for the app session; it is deliberately not persisted across restarts.
 */
class LastSelectedTaskSetup {
    var id: String? = null
}

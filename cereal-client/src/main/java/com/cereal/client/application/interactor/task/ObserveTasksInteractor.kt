package com.cereal.client.application.interactor.task

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.repository.TasksRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce

@OptIn(FlowPreview::class)
class ObserveTasksInteractor(
    private val tasksRepository: TasksRepository,
) : FlowInteractor<List<Task>, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<List<Task>> =
        tasksRepository
            .getAllTasks()
            .debounce(DEBOUNCE_MS)

    companion object {
        /**
         * Debounce interval applied to task-list emissions. Rapidly looping scripts can produce
         * status updates every 50 ms; debouncing coalesces these bursts so the UI layer only
         * recomposes at a reasonable rate while remaining visually responsive.
         */
        const val DEBOUNCE_MS = 100L
    }
}

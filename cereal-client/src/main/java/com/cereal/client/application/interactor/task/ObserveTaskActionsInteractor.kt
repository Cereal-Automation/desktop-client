package com.cereal.client.application.interactor.task

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.application.task.TaskActionsState
import com.cereal.client.application.task.TaskManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@OptIn(FlowPreview::class)
class ObserveTaskActionsInteractor(
    private val taskManager: TaskManager,
) : FlowInteractor<TaskActionsState, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<TaskActionsState> = taskManager.taskActions
}

package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.application.task.TaskManager

class StartTaskInteractor(
    private val taskManager: TaskManager,
) : Interactor<Unit, StartTaskInteractor.Params>() {
    override suspend fun run(params: Params) = taskManager.startTask(params.taskId)

    data class Params(
        val taskId: String,
    )
}

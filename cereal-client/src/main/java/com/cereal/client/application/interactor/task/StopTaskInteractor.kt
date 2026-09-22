package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.application.task.TaskManager

class StopTaskInteractor(
    private val taskManager: TaskManager,
) : Interactor<Unit, StopTaskInteractor.Params>() {
    override suspend fun run(params: Params) = taskManager.stopTask(params.taskId)

    data class Params(
        val taskId: String,
    )
}

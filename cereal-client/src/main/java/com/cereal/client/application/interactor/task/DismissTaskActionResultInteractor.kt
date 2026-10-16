package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.application.task.TaskActionResult
import com.cereal.client.application.task.TaskManager

/** Marks a task action result as shown, so it isn't shown again. */
class DismissTaskActionResultInteractor(
    private val taskManager: TaskManager,
) : Interactor<Unit, DismissTaskActionResultInteractor.Params>() {
    override suspend fun run(params: Params) = taskManager.dismissTaskActionResult(params.result)

    data class Params(
        val result: TaskActionResult,
    )
}

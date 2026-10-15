package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.application.task.TaskActionItem
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.script.ScriptPackageInstance

class GetTaskActionsInteractor(
    private val taskManager: TaskManager,
) : Interactor<List<TaskActionItem>, GetTaskActionsInteractor.Params>() {
    override suspend fun run(params: Params) = taskManager.getTaskActions(params.scriptPackageInstance)

    data class Params(
        val scriptPackageInstance: ScriptPackageInstance,
    )
}

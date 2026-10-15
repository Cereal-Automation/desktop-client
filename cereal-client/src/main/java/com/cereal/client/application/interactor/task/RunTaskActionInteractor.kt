package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.script.ScriptPackageInstance

/** Starts a task action on the app-lifetime [TaskManager]; its progress and result come from [ObserveTaskActionsInteractor]. */
class RunTaskActionInteractor(
    private val taskManager: TaskManager,
) : Interactor<Unit, RunTaskActionInteractor.Params>() {
    override suspend fun run(params: Params) = taskManager.startTaskAction(params.scriptPackageInstance, params.actionName)

    data class Params(
        val scriptPackageInstance: ScriptPackageInstance,
        val actionName: String,
    )
}

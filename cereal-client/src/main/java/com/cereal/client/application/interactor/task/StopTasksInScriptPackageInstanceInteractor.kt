package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.repository.TasksRepository

class StopTasksInScriptPackageInstanceInteractor(
    private val taskManager: TaskManager,
    private val tasksRepository: TasksRepository,
) : Interactor<Unit, StopTasksInScriptPackageInstanceInteractor.Params>() {
    override suspend fun run(params: Params) {
        tasksRepository
            .getTasks(params.scriptPackageInstance)
            .filter {
                it.status is TaskStatus.Running
            }.forEach {
                taskManager.stopTask(it.id)
            }
    }

    data class Params(
        val scriptPackageInstance: ScriptPackageInstance,
    )
}

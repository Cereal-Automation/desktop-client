package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.repository.TasksRepository

class EditScriptInstanceGroupInteractor(
    private val tasksRepository: TasksRepository,
) : Interactor<Unit, EditScriptInstanceGroupInteractor.Params>() {
    override suspend fun run(params: Params) {
        tasksRepository.updateScriptInstanceGroup(ScriptPackageGroup(params.groupId, params.name))
    }

    data class Params(
        val groupId: String,
        val name: String,
    )
}

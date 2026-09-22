package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.repository.TasksRepository
import java.util.UUID

class CreateScriptInstanceGroupInteractor(
    private val tasksRepository: TasksRepository,
) : Interactor<ScriptPackageGroup, CreateScriptInstanceGroupInteractor.Params>() {
    override suspend fun run(params: Params): ScriptPackageGroup =
        tasksRepository.createScriptInstanceGroup(
            ScriptPackageGroup(
                id = UUID.randomUUID().toString(),
                name = params.name,
            ),
        )

    data class Params(
        val name: String,
    )
}

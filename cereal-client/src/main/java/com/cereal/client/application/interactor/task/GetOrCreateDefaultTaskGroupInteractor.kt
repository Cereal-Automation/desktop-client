package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.repository.TasksRepository
import kotlinx.coroutines.flow.firstOrNull

class GetOrCreateDefaultTaskGroupInteractor(
    private val tasksRepository: TasksRepository,
) : Interactor<ScriptPackageGroup, Interactor.None>() {
    override suspend fun run(params: None): ScriptPackageGroup {
        val groups = tasksRepository.getTaskGroups().firstOrNull() ?: emptyList()
        return groups.firstOrNull { it.name == ScriptPackageGroup.DEFAULT_GROUP_NAME }
            ?: tasksRepository.createScriptInstanceGroup(ScriptPackageGroup.createDefault())
    }
}

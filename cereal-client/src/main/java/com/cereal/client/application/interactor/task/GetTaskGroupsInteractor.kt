package com.cereal.client.application.interactor.task

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.repository.TasksRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@OptIn(FlowPreview::class)
class GetTaskGroupsInteractor(
    private val tasksRepository: TasksRepository,
) : FlowInteractor<List<ScriptPackageGroup>, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<List<ScriptPackageGroup>> =
        tasksRepository
            .getTaskGroups()
            .map { groups ->
                groups.sortedBy { it.name.lowercase() }
            }
}

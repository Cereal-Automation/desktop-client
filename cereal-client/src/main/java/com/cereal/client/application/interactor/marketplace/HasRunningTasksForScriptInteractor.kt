package com.cereal.client.application.interactor.marketplace

import com.cereal.client.application.FlowInteractor
import com.cereal.client.domain.repository.TasksRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@OptIn(FlowPreview::class)
class HasRunningTasksForScriptInteractor(
    private val tasksRepository: TasksRepository,
) : FlowInteractor<Boolean, HasRunningTasksForScriptInteractor.Params>() {
    override suspend fun run(params: Params): Flow<Boolean> =
        tasksRepository.getAllTasks().map { tasks ->
            tasks.any { task ->
                task.status.isRunning() &&
                    task.scriptInstance.packageInstance.definition.manifest.packageName == params.packageName
            }
        }

    data class Params(
        val packageName: String,
    )
}

package com.cereal.client.application.interactor.marketplace

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.TasksRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@OptIn(FlowPreview::class)
class GetPackagesWithRunningTasksInteractor(
    private val tasksRepository: TasksRepository,
) : FlowInteractor<Set<String>, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<Set<String>> =
        tasksRepository.getAllTasks().map { tasks ->
            tasks
                .asSequence()
                .filter { it.status.isRunning() }
                .map { it.scriptInstance.packageInstance.definition.manifest.packageName }
                .toSet()
        }
}

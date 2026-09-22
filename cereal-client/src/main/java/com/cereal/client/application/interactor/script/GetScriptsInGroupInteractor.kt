package com.cereal.client.application.interactor.script

import com.cereal.client.application.FlowInteractor
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.getScriptPackageInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.TasksRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@OptIn(FlowPreview::class)
class GetScriptsInGroupInteractor(
    private val scriptInstanceRepository: ScriptInstanceRepository,
    private val tasksRepository: TasksRepository,
) : FlowInteractor<List<ScriptInstanceInGroup>, GetScriptsInGroupInteractor.Params>() {
    override suspend fun run(params: Params): Flow<List<ScriptInstanceInGroup>> {
        val scriptInstancesFlow = scriptInstanceRepository.getScriptPackageInstancesInGroupFlow(params.groupId)

        return combine(scriptInstancesFlow, tasksRepository.getAllTasks()) { scriptInstances, tasks ->
            scriptInstances.map {
                ScriptInstanceInGroup(
                    it,
                    tasks.getCountForScriptInstance(it),
                    tasks.getRunningCountForScriptInstance(it),
                )
            }
        }
    }

    data class Params(
        val groupId: String,
    )
}

fun List<JobTask>.getCountForScriptInstance(scriptPackageInstance: ScriptPackageInstance): Int {
    // Note: this is somewhat inefficient, maybe add some caching.
    return this.count {
        it.scriptInstance.getScriptPackageInstance().id == scriptPackageInstance.id
    }
}

private fun List<JobTask>.getRunningCountForScriptInstance(scriptPackageInstance: ScriptPackageInstance): Int =
    this.count {
        it.scriptInstance.getScriptPackageInstance().id == scriptPackageInstance.id && it.status.isRunning()
    }

data class ScriptInstanceInGroup(
    val scriptPackageInstance: ScriptPackageInstance,
    val taskCount: Int,
    val runningTaskCount: Int,
)

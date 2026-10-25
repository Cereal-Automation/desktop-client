package com.cereal.client.application.interactor.script

import com.cereal.client.application.FlowInteractor
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.getScriptPackageInstance
import com.cereal.client.domain.model.task.TaskStatus
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
            scriptInstances.map { instance ->
                val instanceTasks = tasks.filter { it.scriptInstance.getScriptPackageInstance().id == instance.id }
                ScriptInstanceInGroup(
                    instance,
                    instanceTasks.size,
                    instanceTasks.count { it.status.isRunning() },
                    erroredTaskCount = instanceTasks.count { it.status is TaskStatus.Error },
                    awaitingUserTaskCount = instanceTasks.count { it.userInteraction != null },
                )
            }
        }
    }

    data class Params(
        val groupId: String,
    )
}

data class ScriptInstanceInGroup(
    val scriptPackageInstance: ScriptPackageInstance,
    val taskCount: Int,
    val runningTaskCount: Int,
    val erroredTaskCount: Int = 0,
    /** Tasks waiting for the user (browser, text input, Continue). */
    val awaitingUserTaskCount: Int = 0,
)

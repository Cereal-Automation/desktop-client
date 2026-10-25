package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.application.exception.TaskSetupHasRunningTasksException
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.TasksRepository

/**
 * Saves an edited task setup in place. Its existing tasks keep their ids and status history and pick up the new
 * configuration. Refused with [TaskSetupHasRunningTasksException] while any of its tasks runs.
 */
class UpdateScriptPackageInstanceInteractor(
    private val scriptInstanceRepository: ScriptInstanceRepository,
    private val tasksRepository: TasksRepository,
    private val taskManager: TaskManager,
) : Interactor<Unit, UpdateScriptPackageInstanceInteractor.Params>() {
    override suspend fun run(params: Params) {
        if (tasksRepository.getTasks(params.scriptPackageInstance).any { it.status.isRunning() }) {
            throw TaskSetupHasRunningTasksException()
        }
        scriptInstanceRepository.updateScriptPackageInstance(params.scriptPackageInstance)
        taskManager.reconfigureTasks(scriptInstanceRepository.getScriptPackageInstance(params.scriptPackageInstance.id))
    }

    data class Params(
        val scriptPackageInstance: ScriptPackageInstance,
    )
}

package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.application.exception.TaskSetupHasRunningTasksException
import com.cereal.client.application.script.TaskSetupLimits
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.TasksRepository

/**
 * Saves an edited task setup in place and returns it as stored. Its existing tasks keep their ids and status history
 * and pick up the new configuration. Refused with [TaskSetupHasRunningTasksException] while any of its tasks runs, and
 * held to the [TaskSetupLimits] creation applies (concurrency conflicts only until the user accepts them).
 */
class UpdateScriptPackageInstanceInteractor(
    private val scriptInstanceRepository: ScriptInstanceRepository,
    private val tasksRepository: TasksRepository,
    private val taskManager: TaskManager,
    private val taskSetupLimits: TaskSetupLimits,
) : Interactor<ScriptPackageInstance, UpdateScriptPackageInstanceInteractor.Params>() {
    override suspend fun run(params: Params): ScriptPackageInstance {
        val setup = params.scriptPackageInstance
        if (tasksRepository.getTasks(setup).any { it.status.isRunning() }) {
            throw TaskSetupHasRunningTasksException()
        }
        taskSetupLimits.checkEntitlement(setup.definition, setup.mainConfiguration, setup.variantId)
        if (!params.ignoreConcurrencyConflicts) {
            taskSetupLimits.checkConcurrency(setup.definition, setup.mainConfiguration, setup.childConfigurations, setup.numberOfConcurrentTasks, setup.variantId)
        }
        scriptInstanceRepository.updateScriptPackageInstance(setup)
        val stored = scriptInstanceRepository.getScriptPackageInstance(setup.id)
        taskManager.reconfigureTasks(stored)
        return stored
    }

    data class Params(
        val scriptPackageInstance: ScriptPackageInstance,
        val ignoreConcurrencyConflicts: Boolean = false,
    )
}

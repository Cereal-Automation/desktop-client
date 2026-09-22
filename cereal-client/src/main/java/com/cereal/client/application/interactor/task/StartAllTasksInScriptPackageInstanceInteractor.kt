package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.application.script.hasValidConfiguration
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.exception.InvalidScriptConfigurationException
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.repository.ScriptInstanceRepository

/**
 * Start all tasks in a ScriptPackageInstance, taking the task concurrency into account.
 */
class StartAllTasksInScriptPackageInstanceInteractor(
    private val taskManager: TaskManager,
    private val scriptInstanceRepository: ScriptInstanceRepository,
) : Interactor<Unit, StartAllTasksInScriptPackageInstanceInteractor.Params>() {
    override suspend fun run(params: Params) {
        if (!params.scriptPackageInstance.hasValidConfiguration()) {
            throw InvalidScriptConfigurationException()
        }

        // Main and all child script have separated concurrency limit so start all of them independently.
        val scriptInstances = scriptInstanceRepository.getScriptInstances(params.scriptPackageInstance)
        scriptInstances.forEach {
            taskManager.startTasksToConcurrencyLimit(it)
        }
    }

    data class Params(
        val scriptPackageInstance: ScriptPackageInstance,
    )
}

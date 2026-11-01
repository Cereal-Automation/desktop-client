package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.application.script.hasValidConfiguration
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.exception.InvalidScriptConfigurationException
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.TasksRepository

/**
 * Start all tasks in a ScriptPackageInstance, taking the task concurrency into account. Returns the ids of its tasks,
 * so the caller can land on one.
 */
class StartAllTasksInScriptPackageInstanceInteractor(
    private val taskManager: TaskManager,
    private val scriptInstanceRepository: ScriptInstanceRepository,
    private val tasksRepository: TasksRepository,
) : Interactor<List<String>, StartAllTasksInScriptPackageInstanceInteractor.Params>() {
    override suspend fun run(params: Params): List<String> {
        if (!params.scriptPackageInstance.hasValidConfiguration()) {
            throw InvalidScriptConfigurationException()
        }

        // Main and all child script have separated concurrency limit so start all of them independently.
        val scriptInstances = scriptInstanceRepository.getScriptInstances(params.scriptPackageInstance)
        scriptInstances.forEach {
            taskManager.startTasksToConcurrencyLimit(it)
        }
        return tasksRepository.getTasks(params.scriptPackageInstance).map { it.id }
    }

    data class Params(
        val scriptPackageInstance: ScriptPackageInstance,
    )
}

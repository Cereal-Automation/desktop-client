package com.cereal.client.application.script

import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.TasksRepository

class ScriptInstanceManager(
    private val taskManager: TaskManager,
    private val tasksRepository: TasksRepository,
    private val scriptInstanceRepository: ScriptInstanceRepository,
) {
    suspend fun deleteScriptInstance(scriptPackageInstance: ScriptPackageInstance) {
        tasksRepository.getTasks(scriptPackageInstance).forEach {
            taskManager.deleteTask(it)
        }

        // Remove the script instance.
        scriptInstanceRepository.deleteScriptPackageInstance(scriptPackageInstance)
    }

    /**
     * This will reload the script instance in a 3-step process:
     * 1. Stop all tasks
     * 2. Reload the script instance
     * 3. Restore tasks
     *
     * Note: this will stop any running tasks of the script instance.
     */
    suspend fun reloadScriptInstance(scriptPackageInstance: ScriptPackageInstance) {
        tasksRepository.getTasks(scriptPackageInstance).forEach {
            taskManager.stopTask(it.id)
        }
        val reloadedScriptInstance = scriptInstanceRepository.getScriptPackageInstance(scriptPackageInstance.id)
        taskManager.restoreTasks(reloadedScriptInstance)
    }
}

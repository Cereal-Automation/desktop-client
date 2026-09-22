package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.TasksRepository

class DeleteTaskGroupInteractor(
    private val tasksRepository: TasksRepository,
    private val taskManager: TaskManager,
    private val scriptInstanceRepository: ScriptInstanceRepository,
) : Interactor<Unit, DeleteTaskGroupInteractor.Params>() {
    override suspend fun run(params: Params) {
        // Delete script instances
        scriptInstanceRepository.getScriptPackageInstancesInGroup(params.scriptPackageGroup).forEach {
            // Stop all tasks in this script instance.
            tasksRepository.getTasks(it).forEach { task ->
                taskManager.stopTask(task.id)
            }
            scriptInstanceRepository.deleteScriptPackageInstance(it)
        }

        // Delete the task group, this cascade deletes the script instances.
        tasksRepository.deleteTaskGroup(params.scriptPackageGroup)
    }

    data class Params(
        val scriptPackageGroup: ScriptPackageGroup,
    )
}

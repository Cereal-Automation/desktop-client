package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.application.exception.CannotRemoveRunningTaskException
import com.cereal.client.application.exception.MaxConcurrentTasksReachedException
import com.cereal.client.application.script.TaskSetupLimits
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.concurrentTaskCounts
import com.cereal.client.domain.model.script.runsOnDataset
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.TasksRepository
import java.security.InvalidParameterException

/**
 * Sets a task setup's number of concurrent tasks (the detail header's stepper), within
 * [ScriptPackageInstance.concurrentTaskCounts] and the [TaskSetupLimits] creation applies.
 *
 * On a proxy group the count is the setup's number of tasks: raising it adds idle tasks, lowering it removes idle or
 * finished tasks, highest number first. On a dataset there is one task per record, so the count only sets how many of
 * them run at once.
 */
class SetConcurrentTaskCountInteractor(
    private val scriptInstanceRepository: ScriptInstanceRepository,
    private val tasksRepository: TasksRepository,
    private val taskManager: TaskManager,
    private val taskSetupLimits: TaskSetupLimits,
) : Interactor<Unit, SetConcurrentTaskCountInteractor.Params>() {
    override suspend fun run(params: Params) {
        if (params.count < 1) throw InvalidParameterException("A task setup has at least one task.")
        val setup = scriptInstanceRepository.getScriptPackageInstance(params.scriptPackageInstance.id)
        val tasks = tasksRepository.getTasks(mainScriptOf(setup))
        val counts = setup.concurrentTaskCounts(tasks) ?: 1..1
        if (params.count < counts.first) throw CannotRemoveRunningTaskException()
        if (params.count > counts.last) throw MaxConcurrentTasksReachedException(counts.last)
        taskSetupLimits.checkEntitlement(setup.definition, setup.mainConfiguration)
        scriptInstanceRepository.updateScriptPackageInstance(setup.copy(numberOfConcurrentTasks = params.count))
        if (setup.runsOnDataset()) return
        tasks
            .filterNot { it.status.isRunning() }
            .sortedByDescending { it.createdAt }
            .take((tasks.size - params.count).coerceAtLeast(0))
            .forEach { taskManager.deleteTask(it) }
        taskManager.createTasks(mainScriptOf(setup))
    }

    // Fetched after an update, so it carries the setup's stored values.
    private suspend fun mainScriptOf(setup: ScriptPackageInstance): ScriptInstance = scriptInstanceRepository.getScriptInstances(setup).first { it is MainScriptInstance }

    data class Params(
        val scriptPackageInstance: ScriptPackageInstance,
        val count: Int,
    )
}

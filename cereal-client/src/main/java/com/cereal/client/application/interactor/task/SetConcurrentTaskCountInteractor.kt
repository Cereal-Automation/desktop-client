package com.cereal.client.application.interactor.task

import com.cereal.client.application.Interactor
import com.cereal.client.application.exception.CannotRemoveRunningTaskException
import com.cereal.client.application.exception.MaxConcurrentTasksReachedException
import com.cereal.client.application.interactor.script.validator.ScriptConcurrencyValidator
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.isGroup
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.TasksRepository

/**
 * Sets a task setup's number of concurrent tasks (the detail header's stepper), within [concurrentTaskCounts].
 *
 * On a proxy group the count is the setup's number of tasks: raising it adds idle tasks, lowering it removes idle or
 * finished tasks, highest number first. On a dataset there is one task per record, so the count only sets how many of
 * them run at once.
 */
class SetConcurrentTaskCountInteractor(
    private val scriptInstanceRepository: ScriptInstanceRepository,
    private val tasksRepository: TasksRepository,
    private val taskManager: TaskManager,
) : Interactor<Unit, SetConcurrentTaskCountInteractor.Params>() {
    override suspend fun run(params: Params) {
        require(params.count >= 1) { "A task setup has at least one task." }
        val setup = scriptInstanceRepository.getScriptPackageInstance(params.scriptPackageInstance.id)
        val tasks = tasksRepository.getTasks(mainScriptOf(setup))
        val counts = concurrentTaskCounts(setup, tasks) ?: 1..1
        if (params.count < counts.first) throw CannotRemoveRunningTaskException()
        if (params.count > counts.last) throw MaxConcurrentTasksReachedException(counts.last)
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

/**
 * The counts [setup] may be given, where [mainTasks] are the tasks of its main script; null when its count is fixed at
 * one because its configuration has no proxy group or dataset. Lowering the count never removes a running task.
 */
fun concurrentTaskCounts(
    setup: ScriptPackageInstance,
    mainTasks: List<Task>,
): IntRange? {
    if (setup.mainConfiguration.values.none { it.isGroup }) return null
    val lowest = if (setup.runsOnDataset()) 1 else maxOf(1, mainTasks.count { it.status.isRunning() })
    // The limits creation applies: the form's maximum, and no more than a dataset's records (creation warns that it
    // limits the count to them). A count already above them stays valid, it just can't be raised.
    val cap =
        ScriptConcurrencyValidator(setup.definition, setup.mainConfiguration, setup.childConfigurations, MAX_CONCURRENT_TASKS)
            .validateTaskLimits()
            ?.numberOfRecords
            ?.coerceAtLeast(1) ?: MAX_CONCURRENT_TASKS
    return lowest..maxOf(cap, setup.numberOfConcurrentTasks)
}

/** The most concurrent tasks a setup can have. */
const val MAX_CONCURRENT_TASKS = 25

// Tasks follow the dataset's records (see TaskConfigurationBuilder), not the count.
private fun ScriptPackageInstance.runsOnDataset() = mainConfiguration.values.any { it is ConfigValue.CustomDatasetGroupValue }

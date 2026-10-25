package com.cereal.client.domain.model.script

import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.isGroup
import com.cereal.client.domain.model.task.Task

/** The most concurrent tasks a setup can have. */
const val MAX_CONCURRENT_TASKS = 25

/** Its tasks follow the dataset's records (one per record), so the count only sets how many run at once. */
fun ScriptPackageInstance.runsOnDataset(): Boolean = mainConfiguration.values.any { it is ConfigValue.CustomDatasetGroupValue }

/**
 * The counts this setup may be given; [tasks] may include other setups' tasks, only this setup's main-script tasks
 * count. Null when its count is fixed at one because its configuration has no proxy group or dataset. Lowering the
 * count never removes a running task.
 */
fun ScriptPackageInstance.concurrentTaskCounts(tasks: List<Task>): IntRange? {
    if (mainConfiguration.values.none { it.isGroup }) return null
    val running =
        tasks.count {
            it.scriptInstance is MainScriptInstance && it.scriptInstance.packageInstance.id == id && it.status.isRunning()
        }
    val lowest = if (runsOnDataset()) 1 else maxOf(1, running)
    // The limits creation applies: the form's maximum, and no more than a dataset's records (creation warns that it
    // limits the count to them). A count already above them stays valid, it just can't be raised.
    val cap =
        ScriptConcurrencyValidator(definition, mainConfiguration, childConfigurations, MAX_CONCURRENT_TASKS, variantId)
            .validateTaskLimits()
            ?.numberOfRecords
            ?.coerceAtLeast(1) ?: MAX_CONCURRENT_TASKS
    return lowest..maxOf(cap, numberOfConcurrentTasks)
}

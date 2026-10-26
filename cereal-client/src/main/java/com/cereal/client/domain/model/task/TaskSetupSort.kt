@file:OptIn(ExperimentalTime::class)

package com.cereal.client.domain.model.task

import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.getScriptIdentifierValue
import kotlin.time.ExperimentalTime

/** How the Tasks screen orders the task setups within each group; ties fall back to newest first. */
enum class TaskSetupSort(
    val storageValue: String,
) {
    NEWEST_FIRST("newest_first"),
    OLDEST_FIRST("oldest_first"),

    /** By script name, then by the setup's script identifier. */
    NAME("name"),

    /** Setups with a failed task first, then those with a running task, then the rest. */
    NEEDS_ATTENTION("needs_attention"),
    ;

    /** [setups] in this order; [tasksBySetup] holds each setup's tasks by setup id. */
    fun sort(
        setups: List<ScriptPackageInstance>,
        tasksBySetup: Map<String, List<Task>>,
    ): List<ScriptPackageInstance> {
        val newestFirst = compareByDescending<ScriptPackageInstance> { it.createdAt }
        val comparator =
            when (this) {
                NEWEST_FIRST -> {
                    newestFirst
                }

                OLDEST_FIRST -> {
                    compareBy { it.createdAt }
                }

                NAME -> {
                    compareBy<ScriptPackageInstance, String>(String.CASE_INSENSITIVE_ORDER) { it.definition.manifest.name }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.identifier() }
                        .then(newestFirst)
                }

                NEEDS_ATTENTION -> {
                    compareBy<ScriptPackageInstance> { attentionRank(tasksBySetup[it.id].orEmpty()) }.then(newestFirst)
                }
            }
        return setups.sortedWith(comparator)
    }

    private fun ScriptPackageInstance.identifier(): String =
        definition.mainScript.configuration
            .getScriptIdentifierValue(mainConfiguration)
            .orEmpty()

    private fun attentionRank(tasks: List<Task>): Int =
        when {
            tasks.any { it.status is TaskStatus.Error } -> 0
            tasks.any { it.status is TaskStatus.Running } -> 1
            else -> 2
        }

    companion object {
        val Default: TaskSetupSort = NEWEST_FIRST

        fun fromStorageValue(value: String?): TaskSetupSort = entries.firstOrNull { it.storageValue == value } ?: Default
    }
}

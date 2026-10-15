package com.cereal.client.application.task

import com.cereal.client.domain.model.task.JobTask

/** A script-defined task action as shown in a task's menu; it is run by its [name]. */
data class TaskActionItem(
    val name: String,
    val confirm: String?,
)

/** How a task action ended. Plain classes: two equal outcomes are still separate results to show. */
sealed class TaskActionResult {
    /** [message] is what the action returned; null means it had nothing to report. */
    class Completed(
        val message: String?,
    ) : TaskActionResult()

    class Failed(
        val error: Exception,
    ) : TaskActionResult()
}

/** Instance ids with an action running, and finished results not yet shown to the user (oldest first). */
data class TaskActionsState(
    val inProgress: Set<String> = emptySet(),
    val results: List<TaskActionResult> = emptyList(),
)

/** Lists and runs a script's task actions; the real one instantiates script code through [TaskExecutor]. */
interface TaskActionRunner {
    fun actions(task: JobTask): List<TaskActionItem>

    suspend fun run(
        task: JobTask,
        actionName: String,
    ): String?
}

object TaskExecutorActionRunner : TaskActionRunner {
    override fun actions(task: JobTask) = TaskExecutor(task) {}.taskActions().map { TaskActionItem(it.name, it.confirm) }

    override suspend fun run(
        task: JobTask,
        actionName: String,
    ) = TaskExecutor(task) {}.runTaskAction(actionName)
}

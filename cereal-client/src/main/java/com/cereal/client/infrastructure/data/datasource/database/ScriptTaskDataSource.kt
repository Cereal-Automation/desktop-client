package com.cereal.client.infrastructure.data.datasource.database

import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.user.User

/**
 * Persistence operations for tasks and task status history of script instances.
 */
interface ScriptTaskDataSource {
    suspend fun addTask(
        user: User,
        task: Task,
    )

    suspend fun removeTask(
        user: User,
        taskId: String,
    )

    suspend fun addStatusToTask(
        user: User,
        taskId: String,
        status: TaskStatus,
    )

    suspend fun getJobTasksFromHistory(
        user: User,
        scriptInstance: ScriptInstance,
    ): List<JobTask>
}

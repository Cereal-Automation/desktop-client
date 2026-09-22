package com.cereal.client.domain.repository

import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.task.UserInteraction
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

interface TasksRepository {
    suspend fun createScriptInstanceGroup(scriptPackageGroup: ScriptPackageGroup): ScriptPackageGroup

    suspend fun updateScriptInstanceGroup(scriptPackageGroup: ScriptPackageGroup)

    suspend fun deleteTaskGroup(scriptPackageGroup: ScriptPackageGroup)

    suspend fun getTaskGroups(): Flow<List<ScriptPackageGroup>>

    suspend fun addTask(task: JobTask)

    suspend fun addAllTasks(tasks: List<JobTask>)

    suspend fun removeTask(task: JobTask)

    suspend fun removeAllTasks()

    suspend fun getTask(id: String): JobTask?

    suspend fun addStatusHistory(
        taskId: String,
        status: TaskStatus,
    )

    suspend fun setTaskJob(
        id: String,
        job: Job,
    )

    suspend fun setUserInteraction(
        taskId: String,
        userInteraction: UserInteraction?,
    )

    suspend fun getAllTasks(): Flow<List<JobTask>>

    suspend fun getJobTasksFromHistory(scriptInstance: ScriptInstance): List<JobTask>

    suspend fun createPersistedTask(taskId: String)

    suspend fun deletePersistedTask(taskId: String)

    suspend fun getTasks(scriptPackageInstance: ScriptPackageInstance): List<JobTask>

    suspend fun getTasksFlow(scriptPackageInstance: ScriptPackageInstance): Flow<List<JobTask>>

    suspend fun getTasks(scriptInstance: ScriptInstance): List<JobTask>
}

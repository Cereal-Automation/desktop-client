package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.TaskId
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.sdk.component.userinteraction.WebResourceRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

class InMemoryTasksRepository(
    private val userInteractionContinuation: Map<TaskId, WebResourceRequest> = emptyMap(),
    private val textInputContinuation: Map<TaskId, String> = emptyMap(),
) : TasksRepository {
    // Tasks run on their own threads and write status concurrently; guard the maps like TasksRepositoryImpl does.
    private val lock = Mutex()
    private val tasks = mutableMapOf<String, JobTask>()
    private val taskGroups = mutableMapOf<String, ScriptPackageGroup>()
    private val tasksFlow = MutableStateFlow<List<JobTask>>(emptyList())
    private val taskGroupsFlow = MutableStateFlow<List<ScriptPackageGroup>>(emptyList())

    override suspend fun createScriptInstanceGroup(scriptPackageGroup: ScriptPackageGroup): ScriptPackageGroup =
        lock.withLock {
            taskGroups[scriptPackageGroup.id] = scriptPackageGroup
            notifyTaskGroupsFlow()
            scriptPackageGroup
        }

    override suspend fun updateScriptInstanceGroup(scriptPackageGroup: ScriptPackageGroup) =
        lock.withLock {
            taskGroups[scriptPackageGroup.id] = scriptPackageGroup
            notifyTaskGroupsFlow()
        }

    override suspend fun deleteTaskGroup(scriptPackageGroup: ScriptPackageGroup) =
        lock.withLock {
            taskGroups.remove(scriptPackageGroup.id)
            notifyTaskGroupsFlow()
        }

    override suspend fun getTaskGroups(): Flow<List<ScriptPackageGroup>> = taskGroupsFlow

    private fun notifyTaskGroupsFlow() {
        taskGroupsFlow.value = taskGroups.values.toList()
    }

    override suspend fun addTask(task: JobTask) =
        lock.withLock {
            tasks[task.id] = task
            notifyTasksFlow()
        }

    override suspend fun removeTask(task: JobTask) =
        lock.withLock {
            tasks.remove(task.id)
            notifyTasksFlow()
        }

    override suspend fun removeAllTasks() =
        lock.withLock {
            tasks.clear()
            notifyTasksFlow()
        }

    override suspend fun getTask(id: String): JobTask? = lock.withLock { tasks[id] }

    override suspend fun addStatusHistory(
        taskId: String,
        status: TaskStatus,
    ) = lock.withLock {
        tasks[taskId]?.let {
            // Same dedup + cap policy as TasksRepositoryImpl.
            val updatedTask = it.withStatus(status)
            if (updatedTask !== it) {
                tasks[taskId] = updatedTask
                notifyTasksFlow()
            }
        } ?: Unit
    }

    override suspend fun addAllTasks(tasks: List<JobTask>) =
        lock.withLock {
            tasks.forEach { task ->
                this.tasks[task.id] = task
            }
            notifyTasksFlow()
        }

    override suspend fun setTaskJob(
        id: String,
        job: Job,
    ) = lock.withLock {
        tasks[id]?.let {
            val updatedTask = it.copy(job = job)
            tasks[id] = updatedTask
            notifyTasksFlow()
        } ?: Unit
    }

    override suspend fun setUserInteraction(
        taskId: String,
        userInteraction: UserInteraction?,
    ) {
        val found =
            lock.withLock {
                tasks[taskId]?.let {
                    tasks[taskId] = it.copy(userInteraction = userInteraction)
                    notifyTasksFlow()
                    true
                } ?: false
            }
        if (found) {
            userInteractionContinuation[taskId]?.let { webResourceRequest ->
                (userInteraction as? UserInteraction.Browser)?.let {
                    if (userInteraction.shouldFinish(webResourceRequest)) {
                        userInteraction.continuation.resume(webResourceRequest)
                    }
                }
            }

            textInputContinuation[taskId]?.let { textInput ->
                (userInteraction as? UserInteraction.TextInput)?.let {
                    userInteraction.continuation.resume(textInput)
                }
            }
        }
    }

    override suspend fun getAllTasks(): Flow<List<JobTask>> = tasksFlow

    override suspend fun getJobTasksFromHistory(scriptInstance: ScriptInstance): List<JobTask> =
        lock.withLock {
            tasks.values.filter {
                it.scriptInstance.id == scriptInstance.id
            }
        }

    override suspend fun createPersistedTask(taskId: String) {
        // No-op for in-memory implementation
    }

    override suspend fun deletePersistedTask(taskId: String) {
        // No-op for in-memory implementation
    }

    override suspend fun getTasks(scriptPackageInstance: ScriptPackageInstance): List<JobTask> =
        lock.withLock {
            tasks.values.filter {
                it.scriptInstance.packageInstance.id == scriptPackageInstance.id
            }
        }

    override suspend fun getTasksFlow(scriptPackageInstance: ScriptPackageInstance): Flow<List<JobTask>> =
        tasksFlow.map {
            it.filter { task -> task.scriptInstance.packageInstance.id == scriptPackageInstance.id }
        }

    override suspend fun getTasks(scriptInstance: ScriptInstance): List<JobTask> =
        lock.withLock {
            tasks.values.filter {
                it.scriptInstance.id == scriptInstance.id
            }
        }

    private fun notifyTasksFlow() {
        tasksFlow.value = tasks.values.toList()
    }
}

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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

class InMemoryTasksRepository(
    private val userInteractionContinuation: Map<TaskId, WebResourceRequest> = emptyMap(),
    private val textInputContinuation: Map<TaskId, String> = emptyMap(),
) : TasksRepository {
    private val mutex = Mutex()
    private val tasks = mutableMapOf<String, JobTask>()
    private val taskGroups = mutableMapOf<String, ScriptPackageGroup>()
    private val tasksFlow = MutableStateFlow<List<JobTask>>(emptyList())

    // ScriptPackageGroup equality is id-only, so a renamed group yields a list equal to the previous
    // one and a StateFlow would swallow the rename. A replaying SharedFlow emits every update.
    private val taskGroupsFlow =
        MutableSharedFlow<List<ScriptPackageGroup>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            .apply { tryEmit(emptyList()) }

    override suspend fun createScriptInstanceGroup(scriptPackageGroup: ScriptPackageGroup): ScriptPackageGroup {
        mutex.withLock {
            taskGroups[scriptPackageGroup.id] = scriptPackageGroup
            notifyTaskGroupsFlow()
            return scriptPackageGroup
        }
    }

    override suspend fun updateScriptInstanceGroup(scriptPackageGroup: ScriptPackageGroup) {
        mutex.withLock {
            taskGroups[scriptPackageGroup.id] = scriptPackageGroup
            notifyTaskGroupsFlow()
        }
    }

    override suspend fun deleteTaskGroup(scriptPackageGroup: ScriptPackageGroup) {
        mutex.withLock {
            taskGroups.remove(scriptPackageGroup.id)
            notifyTaskGroupsFlow()
        }
    }

    override suspend fun getTaskGroups(): Flow<List<ScriptPackageGroup>> = taskGroupsFlow

    private fun notifyTaskGroupsFlow() {
        taskGroupsFlow.tryEmit(taskGroups.values.toList())
    }

    override suspend fun addTask(task: JobTask) {
        mutex.withLock {
            tasks[task.id] = task
            notifyTasksFlow()
        }
    }

    override suspend fun removeTask(task: JobTask) {
        mutex.withLock {
            tasks.remove(task.id)
            notifyTasksFlow()
        }
    }

    override suspend fun removeAllTasks() {
        mutex.withLock {
            tasks.clear()
            notifyTasksFlow()
        }
    }

    override suspend fun getTask(id: String): JobTask? = tasks[id]

    override suspend fun addStatusHistory(
        taskId: String,
        status: TaskStatus,
    ) {
        mutex.withLock {
            tasks[taskId]?.let {
                // Same status policy (dedup + history cap) as TasksRepositoryImpl.
                val updatedTask = it.withStatus(status)
                if (updatedTask === it) return@withLock
                tasks[taskId] = updatedTask
                notifyTasksFlow()
            }
        }
    }

    override suspend fun addAllTasks(tasks: List<JobTask>) {
        mutex.withLock {
            tasks.forEach { task ->
                this.tasks[task.id] = task
            }
            notifyTasksFlow()
        }
    }

    override suspend fun setTaskJob(
        id: String,
        job: Job,
    ) {
        mutex.withLock {
            tasks[id]?.let {
                val updatedTask = it.copy(job = job)
                tasks[id] = updatedTask
                notifyTasksFlow()
            }
        }
    }

    override suspend fun setUserInteraction(
        taskId: String,
        userInteraction: UserInteraction?,
    ) {
        mutex.withLock {
            tasks[taskId]?.let {
                val updatedTask = it.copy(userInteraction = userInteraction)
                tasks[taskId] = updatedTask
                notifyTasksFlow()

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
    }

    override suspend fun getAllTasks(): Flow<List<JobTask>> = tasksFlow

    override suspend fun getJobTasksFromHistory(scriptInstance: ScriptInstance): List<JobTask> =
        tasksFlow.value.filter {
            it.scriptInstance.id == scriptInstance.id
        }

    override suspend fun createPersistedTask(taskId: String) {
        // No-op for in-memory implementation
    }

    override suspend fun deletePersistedTask(taskId: String) {
        // No-op for in-memory implementation
    }

    override suspend fun getTasks(scriptPackageInstance: ScriptPackageInstance): List<JobTask> =
        tasksFlow.value.filter {
            it.scriptInstance.packageInstance.id == scriptPackageInstance.id
        }

    override suspend fun getTasksFlow(scriptPackageInstance: ScriptPackageInstance): Flow<List<JobTask>> =
        tasksFlow.map {
            it.filter { task -> task.scriptInstance.packageInstance.id == scriptPackageInstance.id }
        }

    override suspend fun getTasks(scriptInstance: ScriptInstance): List<JobTask> =
        tasksFlow.value.filter {
            it.scriptInstance.id == scriptInstance.id
        }

    private fun notifyTasksFlow() {
        tasksFlow.value = tasks.values.toList()
    }
}

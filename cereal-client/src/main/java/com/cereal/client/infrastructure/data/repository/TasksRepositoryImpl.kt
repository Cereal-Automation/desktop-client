package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.ScriptInstanceDataSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent

class TasksRepositoryImpl(
    private val scriptInstanceDataSource: ScriptInstanceDataSource,
    private val scriptInstanceRepository: ScriptInstanceRepository,
    private val userSession: UserSession,
) : TasksRepository,
    KoinComponent {
    private val tasksMutex = Mutex()
    private var tasks: MutableMap<String, JobTask> = mutableMapOf()

    private val tasksFlow = MutableStateFlow<List<JobTask>>(emptyList())

    override suspend fun createScriptInstanceGroup(scriptPackageGroup: ScriptPackageGroup): ScriptPackageGroup =
        scriptInstanceDataSource
            .createScriptInstanceGroup(
                userSession.requireUser(),
                scriptPackageGroup,
            )

    override suspend fun updateScriptInstanceGroup(scriptPackageGroup: ScriptPackageGroup) =
        scriptInstanceDataSource.updateScriptPackageGroup(
            userSession.requireUser(),
            scriptPackageGroup,
        )

    override suspend fun deleteTaskGroup(scriptPackageGroup: ScriptPackageGroup) =
        scriptInstanceDataSource.deleteScriptInstanceGroup(
            userSession.requireUser(),
            scriptPackageGroup.id,
        )

    override suspend fun getTaskGroups(): Flow<List<ScriptPackageGroup>> =
        scriptInstanceDataSource
            .getScriptInstanceGroups(userSession.requireUser())

    override suspend fun addTask(task: JobTask) =
        tasksMutex.withLock {
            tasks[task.id] = task
            notify()
        }

    override suspend fun addAllTasks(tasks: List<JobTask>) =
        tasksMutex.withLock {
            tasks.forEach { task ->
                this.tasks[task.id] = task
            }
            notify()
        }

    override suspend fun removeTask(task: JobTask) =
        tasksMutex.withLock {
            tasks.remove(task.id)
            getKoin().getScopeOrNull(task.id)?.close()
            notify()
        }

    override suspend fun removeAllTasks() =
        tasksMutex.withLock {
            tasks.clear()
            notify()
        }

    private fun getTasks(): List<JobTask> = tasks.values.toList()

    override suspend fun addStatusHistory(
        taskId: String,
        status: TaskStatus,
    ) = tasksMutex.withLock {
        tasks[taskId]?.let { task ->
            val newTask = task.withStatus(status)
            // Dedup: when the status policy returns the same instance nothing changed, so skip
            // persistence and notification to avoid unnecessary StateFlow emissions and UI
            // recomposition during rapid script loops.
            if (newTask === task) {
                return@withLock
            }

            tasks[task.id] = newTask
            scriptInstanceDataSource.addStatusToTask(userSession.requireUser(), taskId, status)
            notify()
        } ?: Unit
    }

    override suspend fun setTaskJob(
        id: String,
        job: Job,
    ) = tasksMutex.withLock {
        tasks[id]?.copy(job = job)?.let {
            tasks[id] = it
            notify()
        } ?: Unit
    }

    override suspend fun getTask(id: String): JobTask? =
        tasksMutex.withLock {
            tasks[id]
        }

    override suspend fun getAllTasks(): Flow<List<JobTask>> = tasksFlow

    override suspend fun getTasks(scriptInstance: ScriptInstance): List<JobTask> =
        tasksMutex.withLock {
            tasks
                .filter {
                    it.value.scriptInstance.id == scriptInstance.id
                }.values
                .toList()
        }

    override suspend fun getTasks(scriptPackageInstance: ScriptPackageInstance): List<JobTask> {
        val tasks = mutableListOf<JobTask>()
        scriptInstanceRepository.getScriptInstances(scriptPackageInstance).forEach {
            tasks.addAll(getTasks(it))
        }
        return tasks
    }

    override suspend fun getTasksFlow(scriptPackageInstance: ScriptPackageInstance): Flow<List<JobTask>> {
        val scriptInstanceIds =
            scriptInstanceRepository.getScriptInstances(scriptPackageInstance).map {
                it.id
            }
        return tasksFlow.map {
            it.filter {
                scriptInstanceIds.contains(it.scriptInstance.id)
            }
        }
    }

    override suspend fun getJobTasksFromHistory(scriptInstance: ScriptInstance): List<JobTask> =
        scriptInstanceDataSource.getJobTasksFromHistory(
            userSession.requireUser(),
            scriptInstance,
        )

    override suspend fun createPersistedTask(taskId: String) =
        tasksMutex.withLock {
            tasks[taskId]?.let {
                scriptInstanceDataSource.addTask(userSession.requireUser(), it)
            } ?: Unit
        }

    override suspend fun deletePersistedTask(taskId: String) {
        scriptInstanceDataSource.removeTask(userSession.requireUser(), taskId)
    }

    override suspend fun setUserInteraction(
        taskId: String,
        userInteraction: UserInteraction?,
    ) = tasksMutex.withLock {
        tasks[taskId]?.copy(userInteraction = userInteraction)?.let {
            tasks[taskId] = it
            notify()
        } ?: Unit
    }

    private suspend fun notify() {
        tasksFlow.emit(getTasks())
    }
}

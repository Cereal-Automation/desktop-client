package com.cereal.client.application.task

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.exception.MaxConcurrentTasksReachedException
import com.cereal.client.domain.model.exception.InvalidScriptConfigurationException
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.getNumberOfConcurrentTasks
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.domain.model.task.filterIdleTasks
import com.cereal.client.domain.model.task.filterRunningTasks
import com.cereal.client.domain.repository.ArtifactRepository
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.client.presentation.tasks.script.overview.configuration.isValid
import com.cereal.sdk.component.userinteraction.UserInteractionCanceledException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class TaskManager(
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val tasksRepository: TasksRepository,
    private val taskConfigurationBuilder: TaskConfigurationBuilder,
    private val scriptInstanceRepository: ScriptInstanceRepository,
    private val jobTaskFactory: JobTaskFactory,
    private val artifactRepository: ArtifactRepository,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val startTaskMutex = Mutex()
    private val startTasksToConcurrencyLimitMutex = Mutex()

    /**
     * This method should only be called once per script instance (at startup).
     */
    suspend fun restoreTasks(scriptPackageInstance: ScriptPackageInstance) {
        scriptInstanceRepository.getScriptInstances(scriptPackageInstance).forEach {
            // First restore the tasks that persisted.
            val persistedTasks = tasksRepository.getJobTasksFromHistory(it)
            tasksRepository.addAllTasks(persistedTasks)

            // Any task that was still running when the application stopped is now a zombie.
            // This isn't a failure (the app was simply closed), so return it to idle.
            persistedTasks.forEach { task ->
                if (task.status is TaskStatus.Running) {
                    tasksRepository.addStatusHistory(
                        task.id,
                        TaskStatus.Idle(
                            "Task was interrupted because the application was closed.",
                            timestamp = Clock.System.now(),
                        ),
                    )
                }
            }

            // Fill up remainder by creating new tasks.
            createTasks(it)
        }
    }

    suspend fun removeAllTasks() {
        tasksRepository.removeAllTasks()
    }

    suspend fun createTasks(scriptInstance: ScriptInstance): List<JobTask> {
        val taskConfigurations = taskConfigurationBuilder.get(scriptInstance)

        val tasks =
            taskConfigurations.map {
                val task =
                    jobTaskFactory.create(
                        UUID.randomUUID().toString(),
                        scriptInstance,
                        it,
                    )
                task
            }
        tasksRepository.addAllTasks(tasks)
        return tasks
    }

    suspend fun deleteTask(task: JobTask) {
        stopTask(task.id)
        // Remove the task's artifact files. The DB rows cascade when the task row is deleted, but the on-disk bytes
        // must be removed explicitly. Only done here (a genuine delete) — never on the restart path, which re-persists
        // the same task id and must keep its artifacts.
        artifactRepository.deleteForTask(task.id)
        tasksRepository.deletePersistedTask(task.id)
        tasksRepository.removeTask(task)
    }

    suspend fun startTask(id: String) {
        val task = tasksRepository.getTask(id) ?: error("No task with id $id found.")
        startTask(task)
    }

    private suspend fun performPreStartCheck(task: JobTask) {
        if (task.job?.isActive == true) {
            error("Task already running.")
        }

        val numberOfRunningTasks =
            tasksRepository.getTasks(task.scriptInstance).filter {
                it.status is TaskStatus.Running
            }
        val numberOfConcurrentTasks = task.scriptInstance.getNumberOfConcurrentTasks()
        if (numberOfRunningTasks.size >= numberOfConcurrentTasks) {
            throw MaxConcurrentTasksReachedException(numberOfConcurrentTasks)
        }

        if (!task.scriptInstance.definition.configuration
                .isValid(task.configuration, isTaskConfiguration = true)
        ) {
            throw InvalidScriptConfigurationException()
        }
    }

    private suspend fun startTask(task: JobTask) =
        startTaskMutex.withLock {
            // Re-fetch the task from the repository to ensure we check the latest state,
            // avoiding a race condition where a stale task object (with a null job) passes
            // the pre-start check after another coroutine has already started the same task.
            val freshTask = tasksRepository.getTask(task.id) ?: return@withLock
            performPreStartCheck(freshTask)

            val executor =
                TaskExecutor(
                    task = freshTask,
                    onStatusChange = { status ->
                        withContext(NonCancellable) {
                            tasksRepository.addStatusHistory(
                                freshTask.id,
                                TaskStatus.Running(status, Clock.System.now()),
                            )
                        }
                    },
                )

            // Check if task needs to be removed as a consequence of a manual restart after it went into error/success state.
            tasksRepository.deletePersistedTask(freshTask.id)

            tasksRepository.createPersistedTask(freshTask.id)
            tasksRepository.addStatusHistory(freshTask.id, TaskStatus.Running("Starting script", Clock.System.now()))
            val job =
                scope.launch(dispatcherProvider.io) {
                    val result = executor.run()
                    withContext(NonCancellable) {
                        tasksRepository.addStatusHistory(freshTask.id, result)
                    }

                    if (result.inFinishedState()) {
                        onTaskFinished(freshTask)
                    }
                }

            tasksRepository.setTaskJob(freshTask.id, job)
        }

    /**
     * Cancels a running task. When the task isn't running this method won't do anything.
     */
    suspend fun stopTask(id: String) {
        val task = tasksRepository.getTask(id) ?: return
        // Only proceed if the task is actually running
        if (!task.status.isRunning()) return

        // Cancel any active user interaction first
        task.userInteraction?.let { userInteraction ->
            cancelUserInteraction(id, userInteraction)
        }

        val message = "Task stopped by user"
        task.job?.cancel(CancellationException(message))
    }

    private suspend fun cancelUserInteraction(
        id: String,
        userInteraction: UserInteraction,
    ) {
        if (userInteraction is UserInteraction.Browser && !userInteraction.continuation.isCompleted) {
            // Cancel the continuation to close the browser window
            userInteraction.continuation.cancel(
                UserInteractionCanceledException(),
            )
        }
        // Clear the user interaction from the task
        tasksRepository.setUserInteraction(id, null)
    }

    private suspend fun onTaskFinished(task: JobTask) {
        // Check if new task can be started to fulfill the concurrency limit.
        startTasksToConcurrencyLimit(task.scriptInstance)
    }

    suspend fun startTasksToConcurrencyLimit(scriptInstance: ScriptInstance) =
        startTasksToConcurrencyLimitMutex.withLock {
            val tasks = tasksRepository.getTasks(scriptInstance)

            val numberOfConcurrentTasks = scriptInstance.getNumberOfConcurrentTasks()
            val activeTasks = tasks.filterRunningTasks()
            val idleTasks = tasks.filterIdleTasks()

            if (activeTasks.size < numberOfConcurrentTasks) {
                idleTasks.take(numberOfConcurrentTasks - activeTasks.size).forEach {
                    startTask(it)
                }
            }
        }
}

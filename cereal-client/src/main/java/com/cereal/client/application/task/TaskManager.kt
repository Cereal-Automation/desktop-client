package com.cereal.client.application.task

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.exception.MaxConcurrentTasksReachedException
import com.cereal.client.domain.model.exception.InvalidScriptConfigurationException
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.getNumberOfConcurrentTasks
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.task.filterIdleTasks
import com.cereal.client.domain.model.task.filterRunningTasks
import com.cereal.client.domain.repository.ArtifactRepository
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.client.presentation.tasks.script.overview.configuration.isValid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class TaskManager(
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val tasksRepository: TasksRepository,
    private val taskConfigurationBuilder: TaskConfigurationBuilder,
    private val scriptInstanceRepository: ScriptInstanceRepository,
    private val jobTaskFactory: JobTaskFactory,
    private val artifactRepository: ArtifactRepository,
    /** Headless only: restore starts main-script tasks left `Running` again instead of parking them as `Idle`. */
    private val resumeInterruptedTasks: Boolean = false,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Set by [shutdown]: jobs ending from here on don't persist their `Idle`, so their tasks stay `Running`. */
    @Volatile
    private var shuttingDown = false

    /** Non-zero while [stopAllTasks] runs: no task starts (a finished task's refill included), so nothing outlives it. */
    private val stoppingAll = AtomicInteger()

    private val resumed = AtomicInteger()
    private val resumeFailures = Collections.synchronizedList(mutableListOf<String>())
    private val resumeReportTaken = AtomicBoolean(false)

    private val startTaskMutex = Mutex()
    private val startTasksToConcurrencyLimitMutex = Mutex()

    /**
     * This method should only be called once per script instance (at startup).
     */
    suspend fun restoreTasks(scriptPackageInstance: ScriptPackageInstance) {
        val toResume = mutableListOf<JobTask>()
        scriptInstanceRepository.getScriptInstances(scriptPackageInstance).forEach {
            // First restore the tasks that persisted.
            val persistedTasks = tasksRepository.getJobTasksFromHistory(it)
            tasksRepository.addAllTasks(persistedTasks)

            // Any task that was still running when the application stopped is now a zombie.
            // This isn't a failure (the app was simply closed), so return it to idle, or resume it (headless).
            // Children never resume: their resumed parent launches them again.
            persistedTasks.forEach { task ->
                if (task.status !is TaskStatus.Running) return@forEach
                if (resumeInterruptedTasks && task.scriptInstance is MainScriptInstance) {
                    toResume += task
                } else {
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
        toResume.forEach { resume(it) }
    }

    /** Starts [task] again through the normal start path; a failed pre-start check parks it as `Idle` with the reason. */
    private suspend fun resume(task: JobTask) {
        try {
            startTask(task, RESUMED)
            resumed.incrementAndGet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            tasksRepository.addStatusHistory(task.id, TaskStatus.Idle("Couldn't resume after restart: ${e.message}", Clock.System.now()))
            resumeFailures += "${task.scriptInstance.packageInstance.definition.manifest.name}: ${e.message}"
        }
    }

    /** The resumes since boot, for the restart report: handed out once per process, and only when resuming is on. */
    fun takeResumeReport(): ResumeReport? =
        if (resumeInterruptedTasks && resumeReportTaken.compareAndSet(false, true)) {
            ResumeReport(resumed.get(), resumeFailures.toList())
        } else {
            null
        }

    /**
     * Process shutdown (SIGTERM): ends every running job without persisting its `Idle`, so those tasks stay `Running`
     * and resume on the next boot. Waits up to [timeout] for the scripts to finish.
     */
    suspend fun shutdown(timeout: Duration = 20.seconds) {
        shuttingDown = true
        val jobs = tasksRepository.getAllTasks().first().mapNotNull { it.job?.takeIf { job -> job.isActive } }
        jobs.forEach { it.cancel(CancellationException("Cereal is shutting down")) }
        withTimeoutOrNull(timeout) { jobs.joinAll() }
    }

    suspend fun removeAllTasks() {
        tasksRepository.removeAllTasks()
    }

    /**
     * Stops every running task and waits up to [timeout] until each job has persisted its final (`Idle`) status.
     * Call this before the session is cleared, since persisting a status needs the user.
     */
    suspend fun stopAllTasks(timeout: Duration = 20.seconds) {
        stoppingAll.incrementAndGet()
        try {
            // Under the start lock, so a start in flight has set its job. A job still active past its final status
            // (a finished task refilling the concurrency limit) is cancelled too.
            val jobs =
                startTaskMutex.withLock {
                    tasksRepository
                        .getAllTasks()
                        .first()
                        .filter { it.status.isRunning() || it.job?.isActive == true }
                        .mapNotNull { cancel(it) }
                }
            withTimeoutOrNull(timeout) { jobs.joinAll() }
        } finally {
            stoppingAll.decrementAndGet()
        }
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

        // By job, not status: a task left `Running` by the previous process has no job until it resumes.
        val numberOfRunningTasks =
            tasksRepository.getTasks(task.scriptInstance).filter {
                it.job?.isActive == true
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

    private suspend fun startTask(
        task: JobTask,
        firstStatus: String = "Starting script",
    ) = startTaskMutex.withLock {
        if (stoppingAll.get() > 0) return@withLock
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
        tasksRepository.addStatusHistory(freshTask.id, TaskStatus.Running(firstStatus, Clock.System.now()))
        // ATOMIC: a job cancelled before it is dispatched still runs, so the executor persists its Idle status.
        @OptIn(DelicateCoroutinesApi::class)
        val job =
            scope.launch(dispatcherProvider.io, start = CoroutineStart.ATOMIC) {
                val result = executor.run()
                // Ended by process shutdown: keep the persisted `Running` so the task resumes on the next boot. Any
                // result counts: Chrome gets the same SIGTERM, so a browser task may end in an error.
                if (shuttingDown) return@launch
                withContext(NonCancellable) {
                    tasksRepository.addStatusHistory(freshTask.id, result)
                }

                if (result.inFinishedState() && !shuttingDown) {
                    onTaskFinished(freshTask)
                }
            }

        tasksRepository.setTaskJob(freshTask.id, job)
    }

    /**
     * Cancels a running task. When the task isn't running this method won't do anything.
     */
    suspend fun stopTask(id: String) {
        // Under the start lock, so a task mid-start (persisted `Running`, job not yet set) is stopped too.
        startTaskMutex.withLock {
            val task = tasksRepository.getTask(id) ?: return
            // Only proceed if the task is actually running
            if (task.status.isRunning()) cancel(task)
        }
    }

    private suspend fun cancel(task: JobTask): Job? {
        // Clear any pending user interaction; cancelling the job cancels a browser prompt it owns.
        if (task.userInteraction != null) tasksRepository.setUserInteraction(task.id, null)
        return task.job?.also { it.cancel(CancellationException("Task stopped by user")) }
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

/** Tasks resumed after a restart, and a "<script>: <reason>" line per task that couldn't resume. */
data class ResumeReport(
    val resumed: Int,
    val failures: List<String>,
)

private const val RESUMED = "Resumed after restart"

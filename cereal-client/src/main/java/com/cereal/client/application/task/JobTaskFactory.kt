package com.cereal.client.application.task

import com.cereal.client.domain.model.ScopeLinker
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.task.UserInteraction
import kotlinx.coroutines.Job
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class JobTaskFactory(
    private val scopeLinker: ScopeLinker,
) {
    /**
     * Creates a new JobTask and sets up its necessary dependency scopes.
     *
     * @param id Unique identifier for the task. Defaults to a randomly generated UUID.
     * @param scriptInstance Instance of the script associated with this task.
     * @param configuration Configuration values for the script and task.
     * @param statusHistory List of status updates for the task. Defaults to an empty list.
     * @param userInteraction An optional user interaction associated with the task.
     * @param createdAt Timestamp when the task was created. Defaults to current time.
     * @param job An optional job instance associated with the task.
     * @return A newly created JobTask instance with linked resource scopes.
     */
    @OptIn(ExperimentalTime::class)
    fun create(
        id: String = UUID.randomUUID().toString(),
        scriptInstance: ScriptInstance,
        configuration: ScriptConfigurationValues,
        statusHistory: List<TaskStatus> = emptyList(),
        userInteraction: UserInteraction? = null,
        createdAt: Instant = Clock.System.now(),
        job: Job? = null,
    ): JobTask {
        val task =
            JobTask(
                id = id,
                scriptInstance = scriptInstance,
                configuration = configuration,
                statusHistory = statusHistory,
                userInteraction = userInteraction,
                createdAt = createdAt,
                job = job,
            )

        scopeLinker.linkTaskToScriptInstance(task)

        return task
    }
}

@file:OptIn(ExperimentalTime::class)

package com.cereal.client.domain.model.task

import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptInstance
import kotlinx.coroutines.CancellableContinuation
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

sealed class UserInteraction {
    /** A browser prompt is pending. The task owns it through `BrowserPromptProvider`; stop the task to cancel it. */
    data class Browser(
        val title: String,
    ) : UserInteraction()

    data class ContinueButton(
        val continuation: CancellableContinuation<Unit>,
    ) : UserInteraction()

    data class TextInput(
        val title: String,
        val description: String,
        val continuation: CancellableContinuation<String>,
    ) : UserInteraction()
}

typealias TaskId = String

interface Task {
    val id: TaskId
    val scriptInstance: ScriptInstance
    val configuration: ScriptConfigurationValues
    val status: TaskStatus
    val statusHistory: List<TaskStatus>
    val userInteraction: UserInteraction?

    val createdAt: Instant
}

sealed class TaskStatus(
    open val message: String?,
    open val timestamp: Instant,
) {
    class Idle(
        override val message: String? = null,
        override val timestamp: Instant,
    ) : TaskStatus(message, timestamp)

    class Running(
        override val message: String? = null,
        override val timestamp: Instant,
    ) : TaskStatus(message, timestamp)

    class Success(
        override val message: String,
        override val timestamp: Instant,
    ) : TaskStatus(message, timestamp)

    class Error(
        override val message: String,
        val stackTrace: String? = null,
        override val timestamp: Instant,
    ) : TaskStatus(message, timestamp)

    fun inFinishedState(): Boolean = this is Success || this is Error

    fun isRunning(): Boolean = this is Running
}

fun <T : Task> List<T>.filterIdleTasks(): List<T> =
    filter {
        it.status is TaskStatus.Idle
    }

fun <T : Task> List<T>.filterRunningTasks(): List<T> =
    filter {
        it.status is TaskStatus.Running
    }

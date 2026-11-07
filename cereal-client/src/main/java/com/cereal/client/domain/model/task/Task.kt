@file:OptIn(ExperimentalTime::class)

package com.cereal.client.domain.model.task

import com.cereal.client.domain.model.notification.ScriptNotification
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.sdk.component.userinteraction.ConfirmationResult
import com.cereal.sdk.component.userinteraction.WebResourceRequest
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

sealed class UserInteraction {
    data class Browser(
        val title: String,
        val url: String? = null,
        val html: String? = null,
        val shouldFinish: (request: WebResourceRequest) -> Boolean,
        val continuation: CancellableContinuation<WebResourceRequest>,
        val headers: Map<String, String>? = null,
        val onStatusUpdate: suspend (message: String) -> Unit = {},
    ) : UserInteraction()

    data class ContinueButton(
        val continuation: CancellableContinuation<Unit>,
    ) : UserInteraction()

    data class TextInput(
        val title: String,
        val description: String,
        val continuation: CancellableContinuation<String>,
    ) : UserInteraction()

    /**
     * A script's `requestConfirmation`, waiting for an answer. Plain text throughout; [url] is the only link.
     * The first [answer] wins (`complete` ignores later ones), so the window and a remote channel can race.
     */
    data class Confirmation(
        val title: String,
        val question: String,
        val details: List<String>,
        val url: String?,
        val confirmLabel: String,
        val declineLabel: String,
        /** When it counts as Expired, or null when it never expires. */
        val expiresAt: Instant?,
        val answer: CompletableDeferred<ConfirmationResult> = CompletableDeferred(),
    ) : UserInteraction() {
        /** The plain heads-up sent on the user's notification channels when this starts waiting. */
        fun headsUp(): ScriptNotification =
            ScriptNotification(
                title = question,
                message = listOfNotNull(url, "Waiting for your answer in Cereal.").joinToString("\n"),
            )
    }
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

/** The task needs the user: it errored or waits for their input (browser, text input, Continue). */
val Task.needsAttention: Boolean
    get() = status is TaskStatus.Error || userInteraction != null

fun <T : Task> List<T>.filterIdleTasks(): List<T> =
    filter {
        it.status is TaskStatus.Idle
    }

fun <T : Task> List<T>.filterRunningTasks(): List<T> =
    filter {
        it.status is TaskStatus.Running
    }

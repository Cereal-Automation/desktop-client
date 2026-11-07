package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.application.interactor.notification.SendNotificationFromScriptInstanceInteractor
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.client.infrastructure.data.notification.telegram.TelegramRemoteApproval
import com.cereal.sdk.component.userinteraction.ConfirmationResult
import com.cereal.sdk.component.userinteraction.UserInteractionComponent
import com.cereal.sdk.component.userinteraction.WebResourceRequest
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class UserInteractionComponentImpl(
    private val tasksRepository: TasksRepository,
    private val taskId: String,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val sendNotification: SendNotificationFromScriptInstanceInteractor,
    private val scriptPackageInstance: ScriptPackageInstance?,
    private val remoteApproval: TelegramRemoteApproval,
) : UserInteractionComponent {
    override suspend fun showUrl(
        title: String,
        url: String,
        headers: Map<String, String>,
        shouldFinish: (request: WebResourceRequest) -> Boolean,
    ): WebResourceRequest =
        createSuspendCoroutine { continuation ->
            UserInteraction.Browser(
                title = title,
                url = url,
                html = null,
                shouldFinish = shouldFinish,
                continuation = continuation,
                headers = headers.ifEmpty { null },
                onStatusUpdate = ::updateStatus,
            )
        }

    override suspend fun showHtml(
        title: String,
        html: String,
        shouldFinish: (request: WebResourceRequest) -> Boolean,
    ): WebResourceRequest =
        createSuspendCoroutine { continuation ->
            UserInteraction.Browser(
                title = title,
                url = null,
                html = html,
                shouldFinish = shouldFinish,
                continuation = continuation,
                headers = null,
                onStatusUpdate = ::updateStatus,
            )
        }

    override suspend fun showContinueButton() {
        createContinueButtonSuspendCoroutine { continuation ->
            UserInteraction.ContinueButton(
                continuation = continuation,
            )
        }
    }

    override suspend fun requestInput(
        title: String,
        description: String,
    ): String =
        createTextInputSuspendCoroutine { continuation ->
            UserInteraction.TextInput(
                title = title,
                description = description,
                continuation = continuation,
            )
        }

    override suspend fun requestConfirmation(
        title: String,
        question: String,
        details: List<String>,
        url: String?,
        confirmLabel: String,
        declineLabel: String,
        timeout: Duration,
    ): ConfirmationResult {
        val confirmation =
            UserInteraction.Confirmation(
                title = title,
                question = question,
                details = details,
                url = url,
                confirmLabel = confirmLabel,
                declineLabel = declineLabel,
                expiresAt = if (timeout.isFinite()) Clock.System.now() + timeout else null,
            )
        tasksRepository.setUserInteraction(taskId, confirmation)
        try {
            val telegram = remoteApproval.targetFor(scriptPackageInstance?.notificationOverrides)
            return coroutineScope {
                launch { sendHeadsUp(confirmation, skipTelegram = telegram != null) }
                telegram?.let { launch { remoteApproval.offer(it, confirmation) } }
                withTimeoutOrNull(timeout) { confirmation.answer.await() }
                // Nobody answered in time; a racing answer that got in first still wins.
                confirmation.answer.complete(ConfirmationResult.Expired)
                confirmation.answer.await()
            }
        } finally {
            // Expired or cancelled: settle the answer so a late one (window, remote) can't win, then clear the row.
            confirmation.answer.complete(ConfirmationResult.Expired)
            withContext(NonCancellable) { tasksRepository.setUserInteraction(taskId, null) }
        }
    }

    /**
     * The plain heads-up on the task's notification channels (no buttons), so someone away from the desk
     * hears a task is waiting. Remote approval (Telegram buttons) replaces this on Telegram when [skipTelegram].
     */
    private suspend fun sendHeadsUp(
        confirmation: UserInteraction.Confirmation,
        skipTelegram: Boolean,
    ) = sendNotification(
        SendNotificationFromScriptInstanceInteractor.Params(
            notification = confirmation.headsUp(),
            taskId = taskId,
            scriptPackageInstance = scriptPackageInstance,
            skipTelegram = skipTelegram,
        ),
    )

    private suspend fun updateStatus(message: String) {
        tasksRepository.addStatusHistory(taskId, TaskStatus.Running(message, Clock.System.now()))
    }

    private suspend fun createSuspendCoroutine(
        browserFactory: (continuation: CancellableContinuation<WebResourceRequest>) -> UserInteraction.Browser,
    ): WebResourceRequest =
        suspendCancellableCoroutine { continuation ->
            val interaction = browserFactory(continuation)
            // Use the calling coroutine's context so the child is cancelled when the parent is.
            CoroutineScope(continuation.context).launch(dispatcherProvider.io) {
                try {
                    tasksRepository.setUserInteraction(taskId, interaction)
                } catch (e: Exception) {
                    // Cancelling with the cause surfaces to the awaiter as a CancellationException, which
                    // TaskExecutor treats as normal cancellation — so the real failure would vanish. Report
                    // it first (CrashReporter filters genuine cancellations) before cancelling.
                    CrashReporter.report(e)
                    continuation.cancel(e)
                }
            }
        }

    private suspend fun createContinueButtonSuspendCoroutine(
        buttonFactory: (continuation: CancellableContinuation<Unit>) -> UserInteraction.ContinueButton,
    ) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val interaction = buttonFactory(continuation)
            CoroutineScope(continuation.context).launch(dispatcherProvider.io) {
                try {
                    tasksRepository.setUserInteraction(taskId, interaction)
                } catch (e: Exception) {
                    // Cancelling with the cause surfaces to the awaiter as a CancellationException, which
                    // TaskExecutor treats as normal cancellation — so the real failure would vanish. Report
                    // it first (CrashReporter filters genuine cancellations) before cancelling.
                    CrashReporter.report(e)
                    continuation.cancel(e)
                }
            }
        }
    }

    private suspend fun createTextInputSuspendCoroutine(
        inputFactory: (continuation: CancellableContinuation<String>) -> UserInteraction.TextInput,
    ): String =
        suspendCancellableCoroutine { continuation ->
            val interaction = inputFactory(continuation)
            CoroutineScope(continuation.context).launch(dispatcherProvider.io) {
                try {
                    tasksRepository.setUserInteraction(taskId, interaction)
                } catch (e: Exception) {
                    // Cancelling with the cause surfaces to the awaiter as a CancellationException, which
                    // TaskExecutor treats as normal cancellation — so the real failure would vanish. Report
                    // it first (CrashReporter filters genuine cancellations) before cancelling.
                    CrashReporter.report(e)
                    continuation.cancel(e)
                }
            }
        }
}

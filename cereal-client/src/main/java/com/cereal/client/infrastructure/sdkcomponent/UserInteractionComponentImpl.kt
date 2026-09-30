package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.domain.provider.BrowserPrompt
import com.cereal.client.domain.provider.BrowserPromptProvider
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.sdk.component.userinteraction.UserInteractionComponent
import com.cereal.sdk.component.userinteraction.WebResourceRequest
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class UserInteractionComponentImpl(
    private val tasksRepository: TasksRepository,
    private val taskId: String,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val browserPromptProvider: BrowserPromptProvider,
) : UserInteractionComponent {
    override suspend fun showUrl(
        title: String,
        url: String,
        headers: Map<String, String>,
        shouldFinish: (request: WebResourceRequest) -> Boolean,
    ): WebResourceRequest =
        awaitBrowserPrompt(
            BrowserPrompt(title = title, url = url, headers = headers.ifEmpty { null }, shouldFinish = shouldFinish),
        )

    override suspend fun showHtml(
        title: String,
        html: String,
        shouldFinish: (request: WebResourceRequest) -> Boolean,
    ): WebResourceRequest = awaitBrowserPrompt(BrowserPrompt(title = title, html = html, shouldFinish = shouldFinish))

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

    private suspend fun updateStatus(message: String) {
        tasksRepository.addStatusHistory(taskId, TaskStatus.Running(message, Clock.System.now()))
    }

    /**
     * Runs the prompt on the task's own coroutine, so stopping the task cancels it; the pending
     * [UserInteraction.Browser] only tells the UI a prompt is open.
     */
    private suspend fun awaitBrowserPrompt(prompt: BrowserPrompt): WebResourceRequest {
        withContext(dispatcherProvider.io) {
            tasksRepository.setUserInteraction(taskId, UserInteraction.Browser(prompt.title))
            updateStatus("Waiting for browser interaction...")
        }
        try {
            return browserPromptProvider.awaitPrompt(prompt)
        } finally {
            withContext(NonCancellable + dispatcherProvider.io) {
                tasksRepository.setUserInteraction(taskId, null)
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

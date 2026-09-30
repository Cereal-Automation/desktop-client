package com.cereal.client.application.interactor.auth

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.application.auth.UserAuthManager
import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.application.interactor.notification.SendGlobalNotificationInteractor
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.provider.SessionLostProvider
import com.cereal.client.domain.provider.SystemProvider
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

/**
 * Handles every session lost while signed in: stops the running tasks through the normal stop path
 * (persisted `Idle`), notifies the global channels, then clears the session. Emits once the session is cleared. It never signs in
 * again. A loss while signed out (a dead stored session at boot) is ignored; boot shows login itself.
 * Losses arriving while one is being handled find the session cleared and are ignored too.
 * A failing step is logged and skipped: every loss still clears the session, and later losses are still handled.
 */
@OptIn(FlowPreview::class)
class HandleSessionLostInteractor(
    private val sessionLostProvider: SessionLostProvider,
    private val userAuthManager: UserAuthManager,
    private val taskManager: TaskManager,
    private val sendGlobalNotificationInteractor: SendGlobalNotificationInteractor,
    private val systemProvider: SystemProvider,
) : FlowInteractor<Unit, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<Unit> =
        sessionLostProvider
            .observeSessionLost()
            .filter { userAuthManager.getAuthenticatedUserFlow().first() != null }
            .map {
                attempt {
                    taskManager.stopAllTasks()
                    // Sent before signing out, while the user-scoped notification settings are still readable.
                    sendGlobalNotificationInteractor(
                        SendGlobalNotificationInteractor.Params(
                            title = "Session lost",
                            message =
                                "Headless mode on ${systemProvider.hostname()} stopped all tasks: the marketplace rejected " +
                                    "the session, usually because you signed in elsewhere. Attach and sign in again.",
                        ),
                    )
                }
                // Clears the session even when a step before it failed (deauthenticate clears it in a finally).
                attempt { userAuthManager.deauthenticate() }
            }

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Handling the lost session failed", e)
            CrashReporter.report(e)
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(HandleSessionLostInteractor::class.java)
    }
}

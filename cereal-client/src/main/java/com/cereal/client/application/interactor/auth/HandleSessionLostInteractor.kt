package com.cereal.client.application.interactor.auth

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.application.auth.UserAuthManager
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.provider.SessionLostProvider
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Handles every session lost while signed in: stops the running tasks through the normal stop path
 * (persisted `Idle`), then clears the session. Emits once the session is cleared. It never signs in
 * again. A loss while signed out (a dead stored session at boot) is ignored; boot shows login itself.
 * Losses arriving while one is being handled find the session cleared and are ignored too.
 */
@OptIn(FlowPreview::class)
class HandleSessionLostInteractor(
    private val sessionLostProvider: SessionLostProvider,
    private val userAuthManager: UserAuthManager,
    private val taskManager: TaskManager,
) : FlowInteractor<Unit, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<Unit> =
        sessionLostProvider
            .observeSessionLost()
            .filter { userAuthManager.getAuthenticatedUserFlow().first() != null }
            .map {
                taskManager.stopAllTasks()
                // #38: send the session-lost client notification here, while the user scope is still open.
                userAuthManager.deauthenticate()
            }
}

package com.cereal.client.application.auth

import com.cereal.client.application.script.ScriptSyncManager
import com.cereal.client.application.script.SyncProgress
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.auth.OAuthProvider
import com.cereal.client.domain.model.discord.rpc.DiscordPresenceBuilder
import com.cereal.client.domain.model.user.User
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.domain.provider.DiscordProvider
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class UserAuthManager(
    private val sessionRepository: SessionRepository,
    private val authProvider: AuthProvider,
    private val discordRepository: DiscordProvider,
    private val scriptSyncManager: ScriptSyncManager,
    private val scriptInstanceRepository: ScriptInstanceRepository,
    private val taskManager: TaskManager,
) {
    /**
     * Returns the user authentication state flow if user is authenticated or null when user is not authenticated.
     */
    suspend fun initialize(): Flow<UserAuthenticatingState>? {
        val existingUser = sessionRepository.getStoredUser()
        if (existingUser != null) {
            return onAuthenticated(existingUser)
        }
        return null
    }

    suspend fun authenticate(
        email: String,
        password: String,
    ): Flow<UserAuthenticatingState> {
        val authenticatedUser = authProvider.authenticate(email, password)

        return onAuthenticated(authenticatedUser)
    }

    suspend fun authenticateWith(provider: OAuthProvider): Flow<UserAuthenticatingState> {
        val authenticatedUser = authProvider.authenticateWith(provider)

        return onAuthenticated(authenticatedUser)
    }

    suspend fun authenticateGuest(): Flow<UserAuthenticatingState> {
        val authenticatedUser = authProvider.authenticateGuest()

        return onAuthenticated(authenticatedUser)
    }

    suspend fun register(
        name: String,
        email: String,
        password: String,
    ): User {
        val authenticatedUser = authProvider.register(name, email, password)

        return authenticatedUser
    }

    suspend fun deauthenticate() {
        discordRepository.disconnect()
        taskManager.removeAllTasks()
        sessionRepository.setSessionUser(null)
    }

    suspend fun getAuthenticatedUserFlow(): Flow<User?> = sessionRepository.getAuthenticatedUserFlow()

    private suspend fun onAuthenticated(user: User): Flow<UserAuthenticatingState> {
        sessionRepository.setSessionUser(user)

        return flow {
            emit(UserAuthenticatingState.InitializingDiscord)
            initializeDiscord()
            syncScripts()
            emit(UserAuthenticatingState.RestoreTasks)
            restoreTasks()
        }
    }

    private suspend fun initializeDiscord() {
        discordRepository.initialize()

        val discordPresence =
            DiscordPresenceBuilder()
                .setDetails("Idle...")
                .setStartTimestamp(Clock.System.now())
                .build()

        discordRepository.updatePresence(discordPresence = discordPresence)
    }

    private suspend fun FlowCollector<UserAuthenticatingState>.syncScripts() {
        // DON'T catch any exceptions here, just let the authentication fail because we might have logged out the user
        // because of repeated failed synchronisation. We don't want to authenticate until this succeeds because
        // else the user might be able to use scripts to which he is not subscribed. Per-script failures are
        // collected inside the sync (SyncProgress.Finished) and don't propagate, so they don't block login.
        scriptSyncManager.syncWithProgress(updateScripts = true).collect { progress ->
            when (progress) {
                is SyncProgress.InProgress -> {
                    emit(UserAuthenticatingState.SyncScripts(progress.completed, progress.total))
                }

                is SyncProgress.Finished -> {
                }
            }
        }
    }

    private suspend fun restoreTasks() {
        scriptInstanceRepository.getScriptPackageInstances().forEach {
            taskManager.restoreTasks(it)
        }
    }
}

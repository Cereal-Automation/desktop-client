package com.cereal.client.application.auth

sealed class UserAuthenticatingState {
    data object InitializingDiscord : UserAuthenticatingState()

    /**
     * Emitted repeatedly while subscribed scripts are synced. [total] is the number of scripts in the
     * sync list (0 until the list has been resolved); [completed] advances to [total] regardless of
     * per-script failures, because syncing is best-effort and never blocks authentication.
     */
    data class SyncScripts(
        val completed: Int,
        val total: Int,
    ) : UserAuthenticatingState()

    data object RestoreTasks : UserAuthenticatingState()
}

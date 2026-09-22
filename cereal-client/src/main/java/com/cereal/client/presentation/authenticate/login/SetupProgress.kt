package com.cereal.client.presentation.authenticate.login

import com.cereal.client.application.auth.UserAuthenticatingState

/**
 * UI-ready model for the post-authentication "Signing you in..." setup view.
 *
 * The headline is constant and rendered by the composable. [current] and [total] are non-null only
 * while subscribed scripts are actively being synced *and* the sync list size is known — which is
 * exactly when the "Syncing scripts... N of M" count line should appear.
 */
data class SetupProgress(
    val current: Int? = null,
    val total: Int? = null,
)

/**
 * Maps an authentication phase to the setup view's progress model. Only the script-sync phase carries
 * a count, and only once the total is known (> 0); every other phase shows just the steady headline.
 */
fun UserAuthenticatingState.toSetupProgress(): SetupProgress =
    when (this) {
        is UserAuthenticatingState.SyncScripts -> {
            if (total > 0) SetupProgress(current = completed, total = total) else SetupProgress()
        }

        UserAuthenticatingState.InitializingDiscord,
        UserAuthenticatingState.RestoreTasks,
        -> {
            SetupProgress()
        }
    }

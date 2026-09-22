package com.cereal.client.application.interactor.bootstrap

data class BootstrapProgress(
    val state: BootstrapState,
    val progress: Float,
)

sealed class BootstrapState {
    object CheckingForUpdates : BootstrapState()

    object BootingUp : BootstrapState()

    object CheckingApplicationFiles : BootstrapState()

    object LoadingScriptConfigurationFiles : BootstrapState()

    object InitializeDiscord : BootstrapState()

    object RestoringTasks : BootstrapState()

    object RestoreUser : BootstrapState()

    object SynchronizeScripts : BootstrapState()

    object ValidatingDirectories : BootstrapState()

    object Finishing : BootstrapState()

    object Finished : BootstrapState()

    /**
     * A user action is needed before boot process can continue.
     */
    data class Interrupted(
        val action: UserAction,
        val continueAt: BootstrapInteractor.BootstrapSequenceIdentifier,
    ) : BootstrapState()
}

sealed class UserAction {
    object AppUpdateRequired : UserAction()

    object AppUpdateAdvised : UserAction()
}

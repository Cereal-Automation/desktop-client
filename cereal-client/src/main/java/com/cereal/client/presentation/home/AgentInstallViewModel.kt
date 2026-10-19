package com.cereal.client.presentation.home

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.agent.GetAiBrowserAgentListingInteractor
import com.cereal.client.application.interactor.agent.InstallAiBrowserAgentInteractor
import com.cereal.client.presentation.view.UiText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.home_install_failed
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AgentInstallState {
    data object Idle : AgentInstallState

    data object Installing : AgentInstallState

    data class Failed(
        val message: UiText,
    ) : AgentInstallState
}

/**
 * The agent's marketplace side on Home: the install card's Install, and the listing's maintenance flag.
 * The composer replaces the card once the installed agent shows up in [HomeViewModel].
 */
class AgentInstallViewModel(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    getAiBrowserAgentListingInteractor: GetAiBrowserAgentListingInteractor,
    private val installAiBrowserAgentInteractor: InstallAiBrowserAgentInteractor,
) {
    private val _installState = mutableStateOf<AgentInstallState>(AgentInstallState.Idle)
    val installState: State<AgentInstallState> = _installState

    /** The agent's marketplace listing is in maintenance mode: a non-blocking banner over the composer. */
    private val _isUnderMaintenance = mutableStateOf(false)
    val isUnderMaintenance: State<Boolean> = _isUnderMaintenance

    init {
        scope.launch(dispatcherProvider.io) {
            // A failed lookup just means no banner; Install surfaces lookup errors itself.
            getAiBrowserAgentListingInteractor(Interactor.None()) { result ->
                if (result is SuspendableResult.Success) {
                    withContext(dispatcherProvider.main) { _isUnderMaintenance.value = result.value.maintenanceMode }
                }
            }
        }
    }

    /** Runs the normal marketplace install. */
    fun onInstall() {
        _installState.value = AgentInstallState.Installing
        scope.launch(dispatcherProvider.io) {
            installAiBrowserAgentInteractor(Interactor.None()) { result ->
                withContext(dispatcherProvider.main) {
                    _installState.value =
                        when (result) {
                            is SuspendableResult.Success -> {
                                AgentInstallState.Idle
                            }

                            is SuspendableResult.Failure -> {
                                AgentInstallState.Failed(
                                    result.error.localizedMessage?.let { UiText.Raw(it) } ?: UiText.Resource(Res.string.home_install_failed),
                                )
                            }
                        }
                }
            }
        }
    }
}

package com.cereal.client.presentation.proxy.provider

import com.cereal.client.domain.model.proxy.ProxyVendor

/** UI model for the connected-provider card. Carries only fields backed by real account data. */
data class ConnectedProviderUiModel(
    val provider: ProxyVendor,
    val providerName: String,
    val brandMark: String,
    val brandColorArgb: Long,
    val availableTrafficGb: String,
    val subUserCount: Int,
    val lastSync: String,
    val connectedAt: String,
)

/** UI model for one option in the provider picker. */
data class ProviderOptionUiModel(
    val provider: ProxyVendor,
    val name: String,
    val brandMark: String,
    val brandColorArgb: Long,
    val tagline: String,
    val available: Boolean,
)

/** State of the Providers section's connected/empty rendering. */
sealed interface ConnectorState {
    data object Loading : ConnectorState

    data object NotConnected : ConnectorState

    data class Connected(
        val card: ConnectedProviderUiModel,
    ) : ConnectorState
}

/** Which step of the connect wizard is showing. */
enum class ConnectWizardStep {
    PROVIDER,
    CONNECT,
}

/** Token-field state machine for the Connect step. */
enum class ConnectTokenStatus {
    IDLE,
    VALIDATING,
    ERROR,
}

/** State of the connect/replace wizard modal. */
sealed interface ConnectWizardState {
    data object Closed : ConnectWizardState

    /**
     * @param replaceMode when true the wizard was opened from "Manage" to replace an existing token,
     *   so it starts on the [ConnectWizardStep.CONNECT] step and skips the provider picker on Back.
     */
    data class Open(
        val step: ConnectWizardStep,
        val selectedProvider: ProxyVendor,
        val token: String,
        val tokenStatus: ConnectTokenStatus,
        val showToken: Boolean,
        val errorMessage: String?,
        val replaceMode: Boolean,
    ) : ConnectWizardState
}

package com.cereal.client.presentation.proxy.provider

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.proxy.provider.ConnectProxyProviderInteractor
import com.cereal.client.application.interactor.proxy.provider.DisconnectProxyProviderInteractor
import com.cereal.client.application.interactor.proxy.provider.GetConnectedProxyProviderInteractor
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.util.InteractorRunner
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the Proxies screen's "Providers" section and the connect/replace modal, kept separate from
 * [com.cereal.client.presentation.proxy.ProxyViewModel] so neither grows unfocused.
 *
 * Only [ProxyVendor.MARSPROXIES] is connectable; the picker shows the rest as "coming soon".
 */
class ProxyProviderViewModel(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val getConnectedProxyProviderInteractor: GetConnectedProxyProviderInteractor,
    private val connectProxyProviderInteractor: ConnectProxyProviderInteractor,
    private val disconnectProxyProviderInteractor: DisconnectProxyProviderInteractor,
    private val errorResolver: ErrorResolver,
) {
    private val interactorRunner = InteractorRunner(scope, dispatcherProvider, errorResolver)
    private val uiMapper = ProxyProviderUiMapper()

    private val _connectorState = mutableStateOf<ConnectorState>(ConnectorState.Loading)
    val connectorState: State<ConnectorState> = _connectorState

    private val _wizardState = mutableStateOf<ConnectWizardState>(ConnectWizardState.Closed)
    val wizardState: State<ConnectWizardState> = _wizardState

    private val _showDisconnectConfirm = mutableStateOf(false)
    val showDisconnectConfirm: State<Boolean> = _showDisconnectConfirm

    val errorAction = errorResolver.errorAction

    val providerOptions: List<ProviderOptionUiModel> = uiMapper.providerOptions()

    init {
        observeConnector()
    }

    private fun observeConnector() {
        scope.launch(dispatcherProvider.io) {
            getConnectedProxyProviderInteractor(GetConnectedProxyProviderInteractor.Params(DEFAULT_PROVIDER))
                .collectLatest { result ->
                    withContext(dispatcherProvider.main) {
                        // The success value is nullable (null = not connected), so handle the result
                        // directly rather than via handleFailureOrElse (which requires a non-null type).
                        when (result) {
                            is SuspendableResult.Success -> {
                                _connectorState.value =
                                    result.value?.let { ConnectorState.Connected(uiMapper.toConnectedCard(it)) }
                                        ?: ConnectorState.NotConnected
                            }

                            is SuspendableResult.Failure -> {
                                errorResolver.setError(result.error)
                            }
                        }
                    }
                }
        }
    }

    fun onConnectClicked() {
        _wizardState.value =
            ConnectWizardState.Open(
                step = ConnectWizardStep.PROVIDER,
                selectedProvider = DEFAULT_PROVIDER,
                token = "",
                tokenStatus = ConnectTokenStatus.IDLE,
                showToken = false,
                errorMessage = null,
                replaceMode = false,
            )
    }

    fun onManageClicked() {
        val provider = (_connectorState.value as? ConnectorState.Connected)?.card?.provider ?: DEFAULT_PROVIDER
        _wizardState.value =
            ConnectWizardState.Open(
                step = ConnectWizardStep.CONNECT,
                selectedProvider = provider,
                token = "",
                tokenStatus = ConnectTokenStatus.IDLE,
                showToken = false,
                errorMessage = null,
                replaceMode = true,
            )
    }

    fun onProviderSelected(provider: ProxyVendor) {
        if (!provider.available) return
        updateWizard { it.copy(selectedProvider = provider) }
    }

    fun onContinueToConnect() {
        updateWizard { it.copy(step = ConnectWizardStep.CONNECT) }
    }

    fun onBackToProvider() {
        updateWizard { it.copy(step = ConnectWizardStep.PROVIDER, tokenStatus = ConnectTokenStatus.IDLE, errorMessage = null) }
    }

    fun onTokenChanged(token: String) {
        updateWizard {
            it.copy(
                token = token,
                // Clear a stale error as soon as the user edits the field.
                tokenStatus = if (it.tokenStatus == ConnectTokenStatus.ERROR) ConnectTokenStatus.IDLE else it.tokenStatus,
                errorMessage = if (it.tokenStatus == ConnectTokenStatus.ERROR) null else it.errorMessage,
            )
        }
    }

    fun onToggleTokenVisibility() {
        updateWizard { it.copy(showToken = !it.showToken) }
    }

    fun onValidateAndConnect() {
        val current = _wizardState.value as? ConnectWizardState.Open ?: return
        if (current.token.isBlank() || current.tokenStatus == ConnectTokenStatus.VALIDATING) return

        updateWizard { it.copy(tokenStatus = ConnectTokenStatus.VALIDATING, errorMessage = null) }

        scope.launch(dispatcherProvider.io) {
            connectProxyProviderInteractor(
                ConnectProxyProviderInteractor.Params(current.selectedProvider, current.token.trim()),
            ) { result ->
                withContext(dispatcherProvider.main) {
                    when (result) {
                        is SuspendableResult.Success -> {
                            // The connector flow refreshes the section; just close the modal.
                            _wizardState.value = ConnectWizardState.Closed
                        }

                        is SuspendableResult.Failure -> {
                            updateWizard {
                                it.copy(
                                    tokenStatus = ConnectTokenStatus.ERROR,
                                    errorMessage = result.error.localizedMessage,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun onCloseWizard() {
        _wizardState.value = ConnectWizardState.Closed
    }

    fun onDisconnectClicked() {
        _showDisconnectConfirm.value = true
    }

    fun onDismissDisconnect() {
        _showDisconnectConfirm.value = false
    }

    fun onConfirmDisconnect() {
        val provider = (_connectorState.value as? ConnectorState.Connected)?.card?.provider ?: DEFAULT_PROVIDER
        _showDisconnectConfirm.value = false
        interactorRunner.launch(disconnectProxyProviderInteractor, DisconnectProxyProviderInteractor.Params(provider))
    }

    private inline fun updateWizard(transform: (ConnectWizardState.Open) -> ConnectWizardState.Open) {
        val current = _wizardState.value as? ConnectWizardState.Open ?: return
        _wizardState.value = transform(current)
    }

    private companion object {
        val DEFAULT_PROVIDER = ProxyVendor.MARSPROXIES
    }
}

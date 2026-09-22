package com.cereal.client.presentation.proxy.provider

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.proxy.provider.ConnectProxyProviderInteractor
import com.cereal.client.application.interactor.proxy.provider.DisconnectProxyProviderInteractor
import com.cereal.client.application.interactor.proxy.provider.GetConnectedProxyProviderInteractor
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyProviderConnectorRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryProxyConnectionProvider
import com.cereal.client.presentation.error.ErrorResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProxyProviderViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    private val validToken = InMemoryProxyConnectionProvider.DEMO_TOKEN

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        connectorRepository: InMemoryProxyProviderConnectorRepository = InMemoryProxyProviderConnectorRepository(),
        connectionProvider: InMemoryProxyConnectionProvider = InMemoryProxyConnectionProvider(connectorRepository),
    ): ProxyProviderViewModel =
        ProxyProviderViewModel(
            scope = CoroutineScope(dispatcher),
            dispatcherProvider = dispatcherProvider,
            getConnectedProxyProviderInteractor = GetConnectedProxyProviderInteractor(connectorRepository),
            connectProxyProviderInteractor = ConnectProxyProviderInteractor(connectionProvider),
            disconnectProxyProviderInteractor = DisconnectProxyProviderInteractor(connectionProvider),
            errorResolver = ErrorResolver(),
        )

    @Test
    fun `connect flow goes idle to validating to closed on a valid token`() {
        val vm = viewModel()
        vm.onConnectClicked()
        vm.onContinueToConnect()
        vm.onTokenChanged(validToken)

        var open = vm.wizardState.value as ConnectWizardState.Open
        assertEquals(ConnectTokenStatus.IDLE, open.tokenStatus)

        vm.onValidateAndConnect()
        // Before the dispatcher runs the launched work, the status is VALIDATING.
        open = vm.wizardState.value as ConnectWizardState.Open
        assertEquals(ConnectTokenStatus.VALIDATING, open.tokenStatus)

        dispatcher.scheduler.advanceUntilIdle()

        // Successful connect closes the modal; the section reflects the connected provider.
        assertInstanceOf(ConnectWizardState.Closed::class.java, vm.wizardState.value)
        assertInstanceOf(ConnectorState.Connected::class.java, vm.connectorState.value)
    }

    @Test
    fun `connect flow transitions to error on an invalid token`() {
        val vm = viewModel()
        vm.onConnectClicked()
        vm.onContinueToConnect()
        vm.onTokenChanged("wrong-token")

        vm.onValidateAndConnect()
        dispatcher.scheduler.advanceUntilIdle()

        val open = vm.wizardState.value as ConnectWizardState.Open
        assertEquals(ConnectTokenStatus.ERROR, open.tokenStatus)
        assertEquals(ConnectorState.NotConnected, vm.connectorState.value)
    }

    @Test
    fun `editing the token after an error resets the status to idle`() {
        val vm = viewModel()
        vm.onConnectClicked()
        vm.onContinueToConnect()
        vm.onTokenChanged("wrong-token")
        vm.onValidateAndConnect()
        dispatcher.scheduler.advanceUntilIdle()

        vm.onTokenChanged("typing-again")

        val open = vm.wizardState.value as ConnectWizardState.Open
        assertEquals(ConnectTokenStatus.IDLE, open.tokenStatus)
    }

    @Test
    fun `manage opens the wizard on the connect step in replace mode`() {
        val vm = viewModel()

        vm.onManageClicked()

        val open = vm.wizardState.value as ConnectWizardState.Open
        assertEquals(ConnectWizardStep.CONNECT, open.step)
        assertEquals(true, open.replaceMode)
    }
}

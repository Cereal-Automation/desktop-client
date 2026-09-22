package com.cereal.client.presentation.proxy.provider

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.proxy.CheckProxiesInGroupInteractor
import com.cereal.client.application.interactor.proxy.GetProxyGroupsInteractor
import com.cereal.client.application.interactor.proxy.provider.GetConnectedProxyProviderInteractor
import com.cereal.client.application.interactor.proxy.provider.SyncProxiesInteractor
import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealth
import com.cereal.client.domain.model.proxy.ProxySession
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.repository.ProxyRepository
import com.cereal.client.domain.service.ProxyHealthChecker
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyProviderConnectorRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryProxyConnectionProvider
import com.cereal.client.presentation.error.ErrorResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SyncProxiesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    private val provider = ProxyVendor.MARSPROXIES
    private val validToken = InMemoryProxyConnectionProvider.DEMO_TOKEN

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** The split fakes plus the proxy repo they share, so the connection provider and the wizard's
     * group/health interactors all observe the same proxy groups. */
    private class Setup(
        val connectionProvider: InMemoryProxyConnectionProvider,
        val connectorRepository: InMemoryProxyProviderConnectorRepository,
        val proxyRepository: ProxyRepository,
    )

    /** A connected, sync-capable setup (backed by a proxy repo so groups are created). */
    private suspend fun connected(
        proxyRepository: ProxyRepository = InMemoryProxyRepository(),
        syncFails: Boolean = false,
    ): Setup {
        val connectorRepository = InMemoryProxyProviderConnectorRepository()
        val connectionProvider =
            InMemoryProxyConnectionProvider(
                connectorRepository = connectorRepository,
                syncFails = syncFails,
                proxyRepository = proxyRepository,
            )
        connectionProvider.connect(provider, validToken)
        return Setup(connectionProvider, connectorRepository, proxyRepository)
    }

    private fun viewModel(
        setup: Setup,
        healthChecker: ProxyHealthChecker = NoopHealthChecker(),
    ): SyncProxiesViewModel =
        SyncProxiesViewModel(
            scope = CoroutineScope(dispatcher),
            dispatcherProvider = dispatcherProvider,
            syncProxiesInteractor = SyncProxiesInteractor(setup.connectionProvider),
            getProxyGroupsInteractor = GetProxyGroupsInteractor(setup.proxyRepository),
            getConnectedProxyProviderInteractor = GetConnectedProxyProviderInteractor(setup.connectorRepository),
            checkProxiesInGroupInteractor = CheckProxiesInGroupInteractor(setup.proxyRepository, healthChecker),
            errorResolver = ErrorResolver(),
        )

    @Test
    fun `sync flow goes configure to syncing to done on success`() =
        runTest(dispatcher) {
            val vm = viewModel(connected())

            vm.onSyncClicked()
            dispatcher.scheduler.advanceUntilIdle()
            var open = vm.state.value as SyncWizardState.Open
            assertEquals(SyncWizardStep.CONFIGURE, open.step)

            vm.onSessionChanged(ProxySession.STICKY)
            vm.onCountChanged(40)
            vm.onTargetModeChanged(SyncTargetMode.NEW)
            vm.onNewGroupNameChanged("US · Checkout")

            vm.onStartSync()
            // Before the launched work runs, the wizard is on the Syncing step.
            open = vm.state.value as SyncWizardState.Open
            assertEquals(SyncWizardStep.SYNCING, open.step)

            dispatcher.scheduler.advanceUntilIdle()

            open = vm.state.value as SyncWizardState.Open
            assertEquals(SyncWizardStep.DONE, open.step)
            val done = requireNotNull(open.done)
            assertEquals(40, done.syncedCount)
            assertEquals("US · Checkout", done.groupName)
        }

    @Test
    fun `sync flow surfaces an inline failure and stays on configure`() =
        runTest(dispatcher) {
            val vm = viewModel(connected(syncFails = true))

            vm.onSyncClicked()
            dispatcher.scheduler.advanceUntilIdle()
            vm.onNewGroupNameChanged("Doomed group")

            vm.onStartSync()
            dispatcher.scheduler.advanceUntilIdle()

            val open = vm.state.value as SyncWizardState.Open
            assertEquals(SyncWizardStep.CONFIGURE, open.step)
            assertTrue(open.failed, "a sync failure should set the inline failed flag")
        }

    @Test
    fun `rotating sync collapses to a single endpoint`() =
        runTest(dispatcher) {
            val vm = viewModel(connected())

            vm.onSyncClicked()
            dispatcher.scheduler.advanceUntilIdle()
            vm.onSessionChanged(ProxySession.ROTATING)
            vm.onNewGroupNameChanged("Rotating pool")

            vm.onStartSync()
            dispatcher.scheduler.advanceUntilIdle()

            val open = vm.state.value as SyncWizardState.Open
            assertEquals(1, requireNotNull(open.done).syncedCount)
        }

    @Test
    fun `re-sync pre-targets the existing group and appends fresh endpoints into it`() =
        runTest(dispatcher) {
            // Share one proxy repo so the group is both listed as a target and appended into.
            val proxyRepository = InMemoryProxyRepository()
            val group =
                ProxyGroup(
                    id = UUID.randomUUID().toString(),
                    name = "US Residential — Checkout",
                    numberOfItems = 0,
                    items = emptySequence(),
                    provider = provider,
                    geoLabel = "United States",
                )
            proxyRepository.createProxyGroup(group)
            // An endpoint already in the group, to prove re-sync appends rather than replaces.
            proxyRepository.createOrUpdateProxy(
                Proxy(id = UUID.randomUUID(), address = "1.2.3.4", port = 8080, username = "u", password = "p"),
                group,
            )

            val vm = viewModel(connected(proxyRepository = proxyRepository))

            vm.onResyncClicked(group.id, provider)
            dispatcher.scheduler.advanceUntilIdle()

            // The wizard reopens on Configure, pre-targeted to the existing group.
            var open = vm.state.value as SyncWizardState.Open
            assertEquals(SyncWizardStep.CONFIGURE, open.step)
            assertEquals(SyncTargetMode.EXISTING, open.targetMode)
            assertEquals(group.id, open.selectedGroupId)

            vm.onCountChanged(20)
            vm.onStartSync()
            dispatcher.scheduler.advanceUntilIdle()

            open = vm.state.value as SyncWizardState.Open
            val done = requireNotNull(open.done)
            assertEquals(false, done.newGroup, "re-sync writes into the existing group, not a new one")
            assertEquals(group.name, done.groupName)
            assertEquals(20, done.syncedCount)
            // 1 pre-existing endpoint + 20 freshly synced = appended, not replaced.
            assertEquals(21, proxyRepository.getProxiesFromGroup(group.id).size)
        }

    @Test
    fun `sync launches a background health check against the synced group without blocking done`() =
        runTest(dispatcher) {
            val healthChecker = RecordingHealthChecker()
            val vm = viewModel(connected(), healthChecker = healthChecker)

            vm.onSyncClicked()
            dispatcher.scheduler.advanceUntilIdle()
            vm.onSessionChanged(ProxySession.STICKY)
            vm.onCountChanged(30)
            vm.onNewGroupNameChanged("US · Checkout")

            vm.onStartSync()
            dispatcher.scheduler.advanceUntilIdle()

            // The Done step is reached and reports the synced count + that a check is running — it never
            // claims healthy/unreachable numbers.
            val open = vm.state.value as SyncWizardState.Open
            assertEquals(SyncWizardStep.DONE, open.step)
            val done = requireNotNull(open.done)
            assertEquals(30, done.syncedCount)
            assertTrue(done.healthCheckRunning, "the done step should report a background health check")

            // The post-sync health check ran against exactly the 30 newly-synced proxies.
            assertEquals(30, healthChecker.checkedIds.size)
        }

    @Test
    fun `run in background dismisses the wizard`() =
        runTest(dispatcher) {
            val vm = viewModel(connected())
            vm.onSyncClicked()
            dispatcher.scheduler.advanceUntilIdle()

            vm.onRunInBackground()

            assertEquals(SyncWizardState.Closed, vm.state.value)
        }

    private class NoopHealthChecker : ProxyHealthChecker {
        override suspend fun check(proxy: Proxy): ProxyHealth = ProxyHealth.Unknown
    }

    /** Records which proxies were health-checked so a test can assert the post-sync check ran. */
    private class RecordingHealthChecker : ProxyHealthChecker {
        val checkedIds = java.util.concurrent.CopyOnWriteArrayList<UUID>()

        override suspend fun check(proxy: Proxy): ProxyHealth {
            checkedIds += proxy.id
            return ProxyHealth.Unknown
        }
    }
}

package com.cereal.client.presentation.proxy.provider

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.proxy.CheckProxiesInGroupInteractor
import com.cereal.client.application.interactor.proxy.GetProxyGroupsInteractor
import com.cereal.client.application.interactor.proxy.provider.GetConnectedProxyProviderInteractor
import com.cereal.client.application.interactor.proxy.provider.SyncProxiesInteractor
import com.cereal.client.domain.model.proxy.ProxyGeoCatalogue
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxySession
import com.cereal.client.domain.model.proxy.ProxySyncConfig
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.model.proxy.SyncTarget
import com.cereal.client.presentation.error.ErrorResolver
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the "Sync new proxies" wizard (Configure → Syncing → Done, plus an inline failure), kept
 * separate from [ProxyProviderViewModel] so neither grows unfocused.
 *
 * The wizard is opened against the currently-connected provider: [onSyncClicked] reads the connector
 * to pre-fill the zero-traffic warning and loads the existing proxy groups as sync targets.
 */
class SyncProxiesViewModel(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val syncProxiesInteractor: SyncProxiesInteractor,
    private val getProxyGroupsInteractor: GetProxyGroupsInteractor,
    private val getConnectedProxyProviderInteractor: GetConnectedProxyProviderInteractor,
    private val checkProxiesInGroupInteractor: CheckProxiesInGroupInteractor,
    private val errorResolver: ErrorResolver,
) {
    private val _state = mutableStateOf<SyncWizardState>(SyncWizardState.Closed)
    val state: State<SyncWizardState> = _state

    val errorAction = errorResolver.errorAction

    /** Opens the wizard for [provider], pre-loading existing groups and the zero-traffic warning. */
    fun onSyncClicked(provider: ProxyVendor = ProxyVendor.MARSPROXIES) {
        open(provider, targetMode = SyncTargetMode.NEW, selectedGroupId = null)
    }

    /**
     * Re-sync: opens the wizard pre-targeted to the existing provider-synced group [groupId], so a sync
     * appends fresh endpoints into it. [loadContext] preserves the pre-selected group.
     */
    fun onResyncClicked(
        groupId: String,
        provider: ProxyVendor = ProxyVendor.MARSPROXIES,
    ) {
        open(provider, targetMode = SyncTargetMode.EXISTING, selectedGroupId = groupId)
    }

    private fun open(
        provider: ProxyVendor,
        targetMode: SyncTargetMode,
        selectedGroupId: String?,
    ) {
        val defaultCountry = ProxyGeoCatalogue.countries.firstOrNull()?.code ?: ProxyGeoCatalogue.UNITED_STATES_CODE
        _state.value =
            SyncWizardState.Open(
                step = SyncWizardStep.CONFIGURE,
                country = defaultCountry,
                state = null,
                city = null,
                session = ProxySession.STICKY,
                count = ProxySyncConfig.DEFAULT_COUNT,
                targetMode = targetMode,
                newGroupName = "",
                selectedGroupId = selectedGroupId,
                existingGroups = emptyList(),
                zeroTraffic = false,
                failed = false,
                done = null,
            )
        loadContext(provider)
    }

    private fun loadContext(provider: ProxyVendor) {
        scope.launch(dispatcherProvider.io) {
            val groups =
                getProxyGroupsInteractor
                    .run(Interactor.None())
                    .first()
                    .map { SyncTargetGroupUiModel(id = it.id, name = it.name, proxyCount = it.numberOfItems.toLong()) }
            // The API is the source of truth for traffic; null connector → no warning.
            val zeroTraffic =
                getConnectedProxyProviderInteractor
                    .run(GetConnectedProxyProviderInteractor.Params(provider))
                    .first()
                    ?.let { it.availableTrafficGb <= 0.0 } ?: false
            withContext(dispatcherProvider.main) {
                update { current ->
                    current.copy(
                        existingGroups = groups,
                        // Default the existing-group picker to the first group so the form is valid the
                        // moment the user switches to it.
                        selectedGroupId = current.selectedGroupId ?: groups.firstOrNull()?.id,
                        zeroTraffic = zeroTraffic,
                    )
                }
            }
        }
    }

    fun onCountryChanged(code: String) {
        // States only apply to the US; clear any stale state when the country changes.
        update { it.copy(country = code, state = null) }
    }

    fun onStateChanged(state: String?) {
        update { it.copy(state = state) }
    }

    fun onCityChanged(city: String) {
        update { it.copy(city = city.ifBlank { null }) }
    }

    fun onSessionChanged(session: ProxySession) {
        update { it.copy(session = session) }
    }

    fun onCountChanged(count: Int) {
        val clamped = count.coerceIn(ProxySyncConfig.MIN_COUNT, ProxySyncConfig.MAX_COUNT)
        update { it.copy(count = clamped) }
    }

    fun onTargetModeChanged(mode: SyncTargetMode) {
        update { it.copy(targetMode = mode) }
    }

    fun onNewGroupNameChanged(name: String) {
        update { it.copy(newGroupName = name) }
    }

    fun onExistingGroupSelected(groupId: String) {
        update { it.copy(selectedGroupId = groupId) }
    }

    /** Runs the configured sync: transitions to Syncing, then Done (or surfaces an inline failure). */
    fun onStartSync(provider: ProxyVendor = ProxyVendor.MARSPROXIES) {
        val current = _state.value as? SyncWizardState.Open ?: return
        if (!current.canSync || current.step == SyncWizardStep.SYNCING) return

        val target =
            when (current.targetMode) {
                SyncTargetMode.NEW -> SyncTarget.NewGroup(current.newGroupName.trim())
                SyncTargetMode.EXISTING -> SyncTarget.ExistingGroup(current.selectedGroupId ?: return)
            }
        val config =
            ProxySyncConfig(
                country = current.country,
                state = current.state?.takeIf { it.isNotBlank() },
                city = current.city?.takeIf { it.isNotBlank() },
                session = current.session,
                count = current.count,
                target = target,
            )

        update { it.copy(step = SyncWizardStep.SYNCING, failed = false) }

        scope.launch(dispatcherProvider.io) {
            syncProxiesInteractor(SyncProxiesInteractor.Params(provider, config)) { result ->
                withContext(dispatcherProvider.main) {
                    when (result) {
                        is SuspendableResult.Success -> {
                            update {
                                it.copy(
                                    step = SyncWizardStep.DONE,
                                    failed = false,
                                    done =
                                        SyncDoneUiModel(
                                            syncedCount = result.value.syncedCount,
                                            groupName = result.value.groupName,
                                            newGroup = current.targetMode == SyncTargetMode.NEW,
                                            healthCheckRunning = true,
                                        ),
                                )
                            }
                            // Kick off a background health check on the synced group so health fills in
                            // on the rows. Fire-and-forget: the Done step is already shown and isn't blocked.
                            launchHealthCheck(result.value.groupId, result.value.groupName)
                        }

                        // A sync failure writes nothing; show an inline retry on the Configure step.
                        is SuspendableResult.Failure -> {
                            update { it.copy(step = SyncWizardStep.CONFIGURE, failed = true) }
                        }
                    }
                }
            }
        }
    }

    /**
     * Launches the existing group health check in the background against the just-synced group. Runs on
     * its own coroutine (not awaited by the sync flow) so the Done step is never blocked; per-proxy
     * results are persisted by the interactor and stream onto the rows via the proxy flows. Only
     * [ProxyGroup.id] is read by the checker, so a lightweight group reference is sufficient.
     */
    private fun launchHealthCheck(
        groupId: String,
        groupName: String,
    ) {
        val groupRef = ProxyGroup(id = groupId, name = groupName, numberOfItems = 0, items = emptySequence())
        scope.launch(dispatcherProvider.io) {
            checkProxiesInGroupInteractor.run(CheckProxiesInGroupInteractor.Params(groupRef)).collect { }
        }
    }

    /** "Run in background" simply dismisses the modal; writes complete on the launched coroutine. */
    fun onRunInBackground() {
        _state.value = SyncWizardState.Closed
    }

    /** From the Done step: go back to Configure to sync another batch. */
    fun onSyncMore() {
        update { it.copy(step = SyncWizardStep.CONFIGURE, failed = false, done = null) }
    }

    fun onClose() {
        _state.value = SyncWizardState.Closed
    }

    private inline fun update(transform: (SyncWizardState.Open) -> SyncWizardState.Open) {
        val current = _state.value as? SyncWizardState.Open ?: return
        _state.value = transform(current)
    }
}

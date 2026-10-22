package com.cereal.client.presentation.myscripts

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.agent.isAiBrowserAgent
import com.cereal.client.application.interactor.marketplace.GetMarketplaceListingsInteractor
import com.cereal.client.application.interactor.marketplace.GetPackagesWithRunningTasksInteractor
import com.cereal.client.application.interactor.marketplace.RemoveMarketplaceScriptInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.script.GetSdkVersionInteractor
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.isClientUpdateRequired
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.error.handleFailureOrElse
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.swiftzer.semver.SemVer

// Constructor parameters are injected dependencies and screen arguments.
@Suppress("LongParameterList")
class MyScriptsViewModel(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val getScriptsInteractor: GetScriptsInteractor,
    private val getSdkVersionInteractor: GetSdkVersionInteractor,
    private val getPackagesWithRunningTasksInteractor: GetPackagesWithRunningTasksInteractor,
    private val getMarketplaceListingsInteractor: GetMarketplaceListingsInteractor,
    private val removeMarketplaceScriptInteractor: RemoveMarketplaceScriptInteractor,
    private val errorResolver: ErrorResolver,
    private val onStartNewInstance: (publicIdentifier: String) -> Unit = {},
) {
    /** The most urgent thing about an installed script, in priority order. */
    enum class InstalledScriptStatus {
        CLIENT_UPDATE_REQUIRED,
        UPDATE_AVAILABLE,
        RUNNING_TASKS,
    }

    data class InstalledScriptRow(
        val scriptPackage: ScriptPackage,
        val listing: MarketplaceScript?,
        val status: InstalledScriptStatus?,
        val hasRunningTasks: Boolean,
        val isRemoving: Boolean,
    ) {
        /** Only rows with a marketplace listing can open the script detail dialog. */
        val isClickable: Boolean get() = listing != null
    }

    private val installedScripts = mutableStateOf<List<ScriptPackage>>(emptyList())
    private val packagesWithRunningTasks = mutableStateOf<Set<String>>(emptySet())
    private val listings = mutableStateOf<Map<String, MarketplaceScript>>(emptyMap())
    private val sdkVersion = mutableStateOf<SemVer?>(null)
    private val removingPackages = mutableStateOf<Set<String>>(emptySet())

    // Package names already sent to the marketplace, so each is looked up at most once.
    private var requestedListings = emptySet<String>()

    val rows: State<List<InstalledScriptRow>> =
        derivedStateOf {
            installedScripts.value.map { pkg ->
                val packageName = pkg.manifest.packageName
                val listing = listings.value[packageName]
                val running = packageName in packagesWithRunningTasks.value
                InstalledScriptRow(
                    scriptPackage = pkg,
                    listing = listing,
                    status = statusOf(pkg, listing, running),
                    hasRunningTasks = running,
                    isRemoving = packageName in removingPackages.value,
                )
            }
        }

    /** True until the first marketplace lookup finishes, successfully or not. */
    val listingsLoading = mutableStateOf(true)
    val selectedScript = mutableStateOf<MarketplaceScript?>(null)
    val confirmRemoveTarget = mutableStateOf<ScriptPackage?>(null)
    val errorAction = errorResolver.errorAction

    init {
        scope.launch(dispatcherProvider.io) {
            getSdkVersionInteractor(Interactor.None()) { result ->
                withContext(dispatcherProvider.main) {
                    if (result is SuspendableResult.Success) {
                        sdkVersion.value = result.value
                    }
                }
            }
        }
        scope.launch(dispatcherProvider.io) {
            getScriptsInteractor(Interactor.None()).collectLatest { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) { list ->
                        installedScripts.value =
                            list
                                .filterNot { it.isAiBrowserAgent }
                                .sortedBy { it.manifest.name.lowercase() }
                        loadMissingListings()
                    }
                }
            }
        }
        scope.launch(dispatcherProvider.io) {
            getPackagesWithRunningTasksInteractor(Interactor.None()).collectLatest { result ->
                withContext(dispatcherProvider.main) {
                    if (result is SuspendableResult.Success) {
                        packagesWithRunningTasks.value = result.value
                    }
                }
            }
        }
    }

    private fun statusOf(
        pkg: ScriptPackage,
        listing: MarketplaceScript?,
        hasRunningTasks: Boolean,
    ): InstalledScriptStatus? {
        val version = sdkVersion.value
        val latestVersionCode = listing?.latestRelease?.versionCode
        return when {
            version != null && pkg.isClientUpdateRequired(version) -> InstalledScriptStatus.CLIENT_UPDATE_REQUIRED
            latestVersionCode != null && pkg.manifest.versionCode < latestVersionCode -> InstalledScriptStatus.UPDATE_AVAILABLE
            hasRunningTasks -> InstalledScriptStatus.RUNNING_TASKS
            else -> null
        }
    }

    private fun loadMissingListings() {
        val missing = installedScripts.value.map { it.manifest.packageName }.toSet() - requestedListings
        if (missing.isEmpty()) {
            // Nothing installed yet; otherwise an earlier lookup still owns the loading flag.
            if (requestedListings.isEmpty()) listingsLoading.value = false
            return
        }
        requestedListings = requestedListings + missing
        scope.launch(dispatcherProvider.io) {
            getMarketplaceListingsInteractor(GetMarketplaceListingsInteractor.Params(missing)) { result ->
                withContext(dispatcherProvider.main) {
                    // A failure is deliberately not surfaced: rows simply stay manifest-only (e.g. offline).
                    if (result is SuspendableResult.Success) {
                        listings.value = listings.value + result.value
                    }
                    listingsLoading.value = false
                }
            }
        }
    }

    fun onRowClicked(row: InstalledScriptRow) {
        selectedScript.value = row.listing ?: return
    }

    fun onDismissDetail() {
        selectedScript.value = null
    }

    fun onStartNewInstance(publicIdentifier: String) {
        selectedScript.value = null
        this.onStartNewInstance.invoke(publicIdentifier)
    }

    /** The detail dialog removed its script; close it, as the row is leaving the list. */
    fun onScriptRemoved() {
        selectedScript.value = null
    }

    fun onRemoveClicked(scriptPackage: ScriptPackage) {
        confirmRemoveTarget.value = scriptPackage
    }

    fun onRemoveDismissed() {
        confirmRemoveTarget.value = null
    }

    fun onRemoveConfirmed() {
        val target = confirmRemoveTarget.value ?: return
        confirmRemoveTarget.value = null
        val packageName = target.manifest.packageName
        removingPackages.value = removingPackages.value + packageName

        scope.launch(dispatcherProvider.io) {
            removeMarketplaceScriptInteractor(RemoveMarketplaceScriptInteractor.Params(target)) { result ->
                withContext(dispatcherProvider.main) {
                    removingPackages.value = removingPackages.value - packageName
                    if (result is SuspendableResult.Failure) {
                        errorResolver.setError(result.error)
                    }
                    // On success, the GetScriptsInteractor flow refreshes the list automatically.
                }
            }
        }
    }
}

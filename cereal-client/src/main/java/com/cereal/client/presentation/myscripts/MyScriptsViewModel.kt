package com.cereal.client.presentation.myscripts

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.agent.canUninstall
import com.cereal.client.application.interactor.marketplace.GetMarketplaceListingsInteractor
import com.cereal.client.application.interactor.marketplace.GetPackagesWithRunningTasksInteractor
import com.cereal.client.application.interactor.marketplace.RemoveMarketplaceScriptInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.script.GetSdkVersionInteractor
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.entryIds
import com.cereal.client.domain.model.script.entryName
import com.cereal.client.domain.model.script.isClientUpdateRequired
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.error.handleFailureOrElse
import com.cereal.client.presentation.tasks.script.overview.ScriptRequest
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
    private val onStartNewInstance: (ScriptRequest) -> Unit = {},
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
        /** The marketplace lookup for this script hasn't finished yet, so it may still gain a listing. */
        val isListingLoading: Boolean,
        /** The variant this row stands for; null for the package's default entry. */
        val variantId: String? = null,
    ) {
        /** Only rows with a marketplace listing can open the script detail dialog. */
        val isClickable: Boolean get() = listing != null

        val name: String
            get() = variantId?.let(scriptPackage::entryName) ?: listing?.title ?: scriptPackage.manifest.name

        /** Uninstall removes the whole jar, so it stays hidden for every row of a package that can't be uninstalled. */
        val canUninstall: Boolean get() = scriptPackage.canUninstall
    }

    private val installedScripts = mutableStateOf<List<ScriptPackage>>(emptyList())
    private val packagesWithRunningTasks = mutableStateOf<Set<String>>(emptySet())
    private val listings = mutableStateOf<Map<String, MarketplaceScript>>(emptyMap())
    private val sdkVersion = mutableStateOf<SemVer?>(null)
    private val removingPackages = mutableStateOf<Set<String>>(emptySet())
    private val pendingListingLookups = mutableStateOf<Set<String>>(emptySet())

    // Package names already sent to the marketplace, so each is looked up at most once.
    private var requestedPackageNames = emptySet<String>()

    val rows: State<List<InstalledScriptRow>> =
        derivedStateOf {
            installedScripts.value
                .flatMap { pkg -> pkg.entryIds.map { pkg to it } }
                .map { (pkg, variantId) ->
                    val packageName = pkg.manifest.packageName
                    val listing = listings.value[packageName]
                    val running = packageName in packagesWithRunningTasks.value
                    InstalledScriptRow(
                        scriptPackage = pkg,
                        listing = listing,
                        status = statusOf(pkg, listing, running),
                        hasRunningTasks = running,
                        isRemoving = packageName in removingPackages.value,
                        isListingLoading = packageName in pendingListingLookups.value,
                        variantId = variantId,
                    )
                }.sortedBy { it.name.lowercase() }
        }

    val selectedScript = mutableStateOf<MarketplaceScript?>(null)

    // The variant of the row whose detail is open, so starting from it opens that variant.
    private var selectedVariantId: String? = null
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
                        installedScripts.value = list
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
        val missing = installedScripts.value.map { it.manifest.packageName }.toSet() - requestedPackageNames
        if (missing.isEmpty()) return
        requestedPackageNames = requestedPackageNames + missing
        pendingListingLookups.value = pendingListingLookups.value + missing
        scope.launch(dispatcherProvider.io) {
            getMarketplaceListingsInteractor(GetMarketplaceListingsInteractor.Params(missing)) { result ->
                withContext(dispatcherProvider.main) {
                    // A failure is deliberately not surfaced: rows simply stay manifest-only (e.g. offline).
                    if (result is SuspendableResult.Success) {
                        listings.value = listings.value + result.value
                    }
                    pendingListingLookups.value = pendingListingLookups.value - missing
                }
            }
        }
    }

    fun onRowClicked(row: InstalledScriptRow) {
        selectedScript.value = row.listing ?: return
        selectedVariantId = row.variantId
    }

    fun onDismissDetail() {
        selectedScript.value = null
        selectedVariantId = null
    }

    fun onStartNewInstance(publicIdentifier: String) {
        selectedScript.value = null
        this.onStartNewInstance.invoke(ScriptRequest(publicIdentifier, selectedVariantId))
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

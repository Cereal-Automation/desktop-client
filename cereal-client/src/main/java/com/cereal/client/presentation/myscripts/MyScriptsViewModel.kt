package com.cereal.client.presentation.myscripts

import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.marketplace.GetPackagesWithRunningTasksInteractor
import com.cereal.client.application.interactor.marketplace.RemoveMarketplaceScriptInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.script.GetSdkVersionInteractor
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

class MyScriptsViewModel(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val getScriptsInteractor: GetScriptsInteractor,
    private val getSdkVersionInteractor: GetSdkVersionInteractor,
    private val getPackagesWithRunningTasksInteractor: GetPackagesWithRunningTasksInteractor,
    private val removeMarketplaceScriptInteractor: RemoveMarketplaceScriptInteractor,
    private val errorResolver: ErrorResolver,
) {
    val installedScripts = mutableStateOf<List<ScriptPackage>>(emptyList())
    val packagesWithRunningTasks = mutableStateOf<Set<String>>(emptySet())

    /** Package names of installed scripts that require a newer Cereal client than the one running. */
    val updateRequiredPackages = mutableStateOf<Set<String>>(emptySet())
    val confirmRemoveTarget = mutableStateOf<ScriptPackage?>(null)
    val removingPackages = mutableStateOf<Set<String>>(emptySet())
    val errorAction = errorResolver.errorAction

    private var sdkVersion: SemVer? = null

    init {
        scope.launch(dispatcherProvider.io) {
            getSdkVersionInteractor(Interactor.None()) { result ->
                withContext(dispatcherProvider.main) {
                    if (result is SuspendableResult.Success) {
                        sdkVersion = result.value
                        recomputeUpdateRequired()
                    }
                }
            }
        }
        scope.launch(dispatcherProvider.io) {
            getScriptsInteractor(Interactor.None()).collectLatest { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) { list ->
                        installedScripts.value = list.sortedBy { it.manifest.name.lowercase() }
                        recomputeUpdateRequired()
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

    private fun recomputeUpdateRequired() {
        val version = sdkVersion ?: return
        updateRequiredPackages.value =
            installedScripts.value
                .filter { it.isClientUpdateRequired(version) }
                .map { it.manifest.packageName }
                .toSet()
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

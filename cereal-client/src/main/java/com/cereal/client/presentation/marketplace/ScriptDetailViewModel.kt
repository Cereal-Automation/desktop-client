package com.cereal.client.presentation.marketplace

import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.exception.ClientUpdateRequiredException
import com.cereal.client.application.interactor.auth.GetAuthenticatedUserInteractor
import com.cereal.client.application.interactor.marketplace.HasRunningTasksForScriptInteractor
import com.cereal.client.application.interactor.marketplace.InstallMarketplaceScriptInteractor
import com.cereal.client.application.interactor.marketplace.IsScriptInstalledInteractor
import com.cereal.client.application.interactor.marketplace.RemoveMarketplaceScriptInteractor
import com.cereal.client.application.interactor.settings.OpenUrlInteractor
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.provider.CheckoutCancelledException
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.error.handleFailureOrElse
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Constructor parameters are injected dependencies and screen arguments.
@Suppress("LongParameterList")
class ScriptDetailViewModel(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val installMarketplaceScriptInteractor: InstallMarketplaceScriptInteractor,
    private val isScriptInstalledInteractor: IsScriptInstalledInteractor,
    private val openUrlInteractor: OpenUrlInteractor,
    private val getAuthenticatedUserInteractor: GetAuthenticatedUserInteractor,
    private val errorResolver: ErrorResolver,
    private val removeMarketplaceScriptInteractor: RemoveMarketplaceScriptInteractor,
    private val hasRunningTasksForScriptInteractor: HasRunningTasksForScriptInteractor,
    private val scriptRepository: ScriptRepository,
    private val onStartNewInstance: (publicIdentifier: String) -> Unit = {},
) {
    val installState = mutableStateOf<InstallState>(InstallState.Loading)
    val confirmRemoveDialog = mutableStateOf(false)
    val hasRunningTasks = mutableStateOf(false)
    private var loadedScript: MarketplaceScript? = null
    private var loadedScriptPackage: ScriptPackage? = null
    private var runningTasksJob: Job? = null

    sealed class InstallState {
        data object Loading : InstallState()

        data object Idle : InstallState()

        data object Installing : InstallState()

        data object AwaitingCheckout : InstallState()

        data object Success : InstallState()

        data object AlreadyInstalled : InstallState()

        data object Removing : InstallState()

        data object GuestPurchaseRequired : InstallState()

        data class Error(
            val message: String,
        ) : InstallState()
    }

    fun onScriptLoaded(script: MarketplaceScript) {
        loadedScript = script
        installState.value = InstallState.Idle
        scope.launch(dispatcherProvider.io) {
            val user = (getAuthenticatedUserInteractor(Interactor.None()).first() as? SuspendableResult.Success)?.value
            if (user?.isGuest == true && !script.isFree) {
                withContext(dispatcherProvider.main) {
                    installState.value = InstallState.GuestPurchaseRequired
                }
                return@launch
            }
            isScriptInstalledInteractor(IsScriptInstalledInteractor.Params(script.publicIdentifier)) { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) { isInstalled ->
                        if (isInstalled) {
                            installState.value = InstallState.AlreadyInstalled
                            runningTasksJob?.cancel()
                            runningTasksJob =
                                scope.launch(dispatcherProvider.io) {
                                    val pkg = scriptRepository.getScript(script.publicIdentifier)
                                    loadedScriptPackage = pkg
                                    if (pkg != null) {
                                        hasRunningTasksForScriptInteractor(
                                            HasRunningTasksForScriptInteractor.Params(pkg.manifest.packageName),
                                        ).collect { res ->
                                            if (res is SuspendableResult.Success) {
                                                withContext(dispatcherProvider.main) {
                                                    hasRunningTasks.value = res.value
                                                }
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }
    }

    fun onInstall(script: MarketplaceScript) {
        scope.launch(dispatcherProvider.io) {
            withContext(dispatcherProvider.main) {
                installState.value = if (script.isFree) InstallState.Installing else InstallState.AwaitingCheckout
            }
            installMarketplaceScriptInteractor(InstallMarketplaceScriptInteractor.Params(script)) { result ->
                withContext(dispatcherProvider.main) {
                    when (result) {
                        is SuspendableResult.Success -> {
                            installState.value = InstallState.Success
                        }

                        is SuspendableResult.Failure -> {
                            when (result.error) {
                                is CheckoutCancelledException -> {
                                    installState.value = InstallState.Idle
                                }

                                is ClientUpdateRequiredException -> {
                                    installState.value = InstallState.Error(UPDATE_REQUIRED_MESSAGE)
                                }

                                else -> {
                                    installState.value = InstallState.Idle
                                    errorResolver.setError(result.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun onOpenUrl(url: String) {
        scope.launch(dispatcherProvider.io) {
            openUrlInteractor(OpenUrlInteractor.Params(url)) { _ -> }
        }
    }

    fun onStartNewInstance() {
        val script = loadedScript ?: return
        this.onStartNewInstance.invoke(script.publicIdentifier)
    }

    fun onRemoveClicked() {
        confirmRemoveDialog.value = true
    }

    fun onRemoveDismissed() {
        confirmRemoveDialog.value = false
    }

    fun onRemoveConfirmed() {
        confirmRemoveDialog.value = false
        val pkg =
            loadedScriptPackage ?: run {
                errorResolver.setError(IllegalStateException("Script package is not loaded; cannot remove."))
                return
            }
        scope.launch(dispatcherProvider.io) {
            withContext(dispatcherProvider.main) {
                installState.value = InstallState.Removing
            }
            removeMarketplaceScriptInteractor(RemoveMarketplaceScriptInteractor.Params(pkg)) { result ->
                withContext(dispatcherProvider.main) {
                    when (result) {
                        is SuspendableResult.Success -> {
                            installState.value = InstallState.Idle
                            loadedScriptPackage = null
                        }

                        is SuspendableResult.Failure -> {
                            installState.value = InstallState.AlreadyInstalled
                            errorResolver.setError(result.error)
                        }
                    }
                }
            }
        }
    }

    companion object {
        // Mirrors R.string.script_update_required_message; resolved here because the install result is handled
        // off the Compose thread.
        private const val UPDATE_REQUIRED_MESSAGE =
            "This script requires a newer version of Cereal. Update your client to use it."
    }
}

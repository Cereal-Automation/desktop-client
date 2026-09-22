package com.cereal.client.presentation.tasks.script.overview

import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.exception.ScriptSyncException
import com.cereal.client.application.interactor.notification.HasNotificationChannelsConfiguredInteractor
import com.cereal.client.application.interactor.script.GetScriptCapacityInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.script.GetSdkVersionInteractor
import com.cereal.client.application.interactor.script.NumberOfConcurrentTasksConflictException
import com.cereal.client.application.interactor.script.StartScriptInteractor
import com.cereal.client.application.interactor.script.SyncScriptsOnScriptSelectionInteractor
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.isClientUpdateRequired
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.error.handleFailureOrElse
import com.cereal.client.presentation.model.LoadState
import com.cereal.client.presentation.tasks.script.overview.configuration.ScriptConfigurationViewModel
import com.cereal.client.presentation.util.InteractorRunner
import com.cereal.client.presentation.util.exhaustive
import com.cereal.client.presentation.view.group.GroupListItemContent
import com.cereal.client.presentation.view.group.GroupedListViewModel
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.swiftzer.semver.SemVer
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent

// Constructor parameters are injected dependencies and screen arguments.
@Suppress("LongParameterList")
class ScriptSelectionViewModel(
    private val scope: CoroutineScope,
    val scriptPackageGroup: ScriptPackageGroup,
    private val initialScriptPackageInstance: ScriptPackageInstance?,
    private val initialPublicIdentifier: String? = null,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val getScriptsInteractor: GetScriptsInteractor,
    private val getScriptCapacityInteractor: GetScriptCapacityInteractor,
    private val getSdkVersionInteractor: GetSdkVersionInteractor,
    private val syncScriptsOnScriptSelectionInteractor: SyncScriptsOnScriptSelectionInteractor,
    private val startScriptInteractor: StartScriptInteractor,
    private val hasNotificationChannelsConfiguredInteractor: HasNotificationChannelsConfiguredInteractor,
    private val errorResolver: ErrorResolver,
) {
    private val interactorRunner = InteractorRunner(scope, dispatcherProvider, errorResolver)

    private val scriptsListViewModel =
        GroupedListViewModel<ScriptPackage>(onSelectedItemChange = {
            updateConfig()
        })

    val overviewViewState = mutableStateOf<ScriptSelectionState.Overview>(ScriptSelectionState.Overview.Empty)
    val configurationViewState = mutableStateOf<ScriptSelectionState.Config>(ScriptSelectionState.Config.None)
    val scriptRefreshState = mutableStateOf<LoadState>(LoadState.NotLoading())
    val scriptLaunchLoadState = mutableStateOf<LoadState>(LoadState.NotLoading())
    val scriptInstanceStarted = mutableStateOf<ScriptPackageInstance?>(null)
    val errorAction = errorResolver.errorAction
    private var scriptConfigurationViewModel: ScriptConfigurationViewModel? = null
    val startScriptWarningConfirmation = mutableStateOf<String?>(null)
    val showNoNotificationChannelsWarning = mutableStateOf(false)

    /** True when the currently selected script requires a newer Cereal client; blocks launching. */
    val selectedScriptUpdateRequired = mutableStateOf(false)

    /**
     * Entitled record [ScriptCapacity] of the currently selected script, shown before the run starts.
     * [ScriptCapacity.None] whenever nothing is selected, the script carries no capacity, or the user
     * is not entitled to it.
     */
    val selectedScriptCapacity = mutableStateOf<ScriptCapacity>(ScriptCapacity.None)

    private var sdkVersion: SemVer? = null

    init {
        loadInstalledScripts()
    }

    private fun loadInstalledScripts() {
        scope.launch(dispatcherProvider.io) {
            getSdkVersionInteractor(Interactor.None()) { result ->
                if (result is SuspendableResult.Success) {
                    sdkVersion = result.value
                }
            }
            getScriptsInteractor(Interactor.None()).collectLatest { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) {
                        val items =
                            it.map { script ->
                                val needsUpdate = sdkVersion?.let { version -> script.isClientUpdateRequired(version) } ?: false
                                GroupListItemContent(
                                    id = script,
                                    title = script.manifest.name,
                                    warningMessage = if (needsUpdate) UPDATE_REQUIRED_MESSAGE else null,
                                    badge = if (needsUpdate) UPDATE_REQUIRED_BADGE else null,
                                )
                            }

                        if (items.isEmpty()) {
                            overviewViewState.value = ScriptSelectionState.Overview.Empty
                        } else {
                            scriptsListViewModel.updateItems(items, false)

                            overviewViewState.value = ScriptSelectionState.Overview.Filled(scriptsListViewModel)

                            // Auto-select the script if duplicating from an existing instance.
                            initialScriptPackageInstance?.let { instance ->
                                val matchingScript =
                                    items.find { item ->
                                        item.id.manifest.packageName == instance.definition.manifest.packageName
                                    }
                                matchingScript?.let { scriptItem ->
                                    scriptsListViewModel.selectItem(scriptItem.id)
                                }
                            }

                            if (initialPublicIdentifier != null && initialScriptPackageInstance == null) {
                                val matchingScript =
                                    items.find { item ->
                                        item.id.manifest.packageName == initialPublicIdentifier
                                    }
                                matchingScript?.let { scriptItem ->
                                    scriptsListViewModel.selectItem(scriptItem.id)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateConfig() {
        selectedScriptUpdateRequired.value =
            scriptsListViewModel.selectedItem?.let { selected ->
                sdkVersion?.let { version -> selected.isClientUpdateRequired(version) }
            } ?: false
        // Reset first so a previous script's cap never lingers while the new one's capacity loads.
        selectedScriptCapacity.value = ScriptCapacity.None
        scriptsListViewModel.selectedItem?.let { selectedScript ->
            // Only pass initial instance if the selected script matches the initial instance's script.
            val initialInstance =
                if (initialScriptPackageInstance?.definition?.manifest?.packageName == selectedScript.manifest.packageName) {
                    initialScriptPackageInstance
                } else {
                    null
                }
            val configViewModel =
                KoinJavaComponent.get<ScriptConfigurationViewModel>(
                    ScriptConfigurationViewModel::class.java,
                    parameters = { parametersOf(scope, selectedScript, initialInstance) },
                )
            scriptConfigurationViewModel = configViewModel
            configurationViewState.value = ScriptSelectionState.Config.Selected(configViewModel)
            loadSelectedScriptCapacity(selectedScript)
        } ?: run {
            scriptConfigurationViewModel = null
            configurationViewState.value = ScriptSelectionState.Config.None
        }
    }

    /**
     * Loads the entitled [ScriptCapacity] for [scriptPackage] into [selectedScriptCapacity]. Capacity
     * is display-only on this screen, so a fetch failure degrades silently to [ScriptCapacity.None]
     * rather than routing to the [ErrorResolver] or blocking configuration; the read is cache-backed
     * (no network) and already returns [ScriptCapacity.None] when the script is unlicensed or uncapped.
     */
    private fun loadSelectedScriptCapacity(scriptPackage: ScriptPackage) {
        scope.launch(dispatcherProvider.io) {
            getScriptCapacityInteractor(GetScriptCapacityInteractor.Params(scriptPackage)) { result ->
                withContext(dispatcherProvider.main) {
                    selectedScriptCapacity.value = (result as? SuspendableResult.Success)?.value ?: ScriptCapacity.None
                }
            }
        }
    }

    fun validate(): Boolean = scriptConfigurationViewModel?.validate() ?: false

    fun launchScript() {
        // Check if notification channels are configured (considering script overrides)
        val scriptOverrides = scriptConfigurationViewModel?.notificationOverridesForm?.toOverrides()
        interactorRunner.launch(
            hasNotificationChannelsConfiguredInteractor,
            HasNotificationChannelsConfiguredInteractor.Params(scriptOverrides),
        ) { hasChannels ->
            if (!hasChannels && !showNoNotificationChannelsWarning.value) {
                showNoNotificationChannelsWarning.value = true
            } else {
                launchScriptInternal(false)
            }
        }
    }

    fun onNoNotificationChannelsWarningAccepted() {
        showNoNotificationChannelsWarning.value = false
        launchScriptInternal(false)
    }

    fun onNoNotificationChannelsWarningDismissed() {
        showNoNotificationChannelsWarning.value = false
    }

    private fun launchScriptInternal(ignoreConcurrencyConflicts: Boolean) {
        if (scriptLaunchLoadState.value is LoadState.Loading) {
            return
        }

        if (configurationViewState.value !is ScriptSelectionState.Config.Selected) {
            return
        }

        scriptLaunchLoadState.value = LoadState.Loading()
        val taskGroupId = scriptPackageGroup.id

        scope.launch(dispatcherProvider.io) {
            startScriptInteractor(
                StartScriptInteractor.Params(
                    groupId = taskGroupId,
                    scriptPackage = scriptsListViewModel.selectedItem!!,
                    mainScriptConfiguration =
                        scriptConfigurationViewModel
                            ?.scriptConfigurationForm
                            ?.value
                            ?.mainConfigurationFormSection
                            ?.getScriptConfigurationValues()
                            ?: throw RuntimeException("No scriptConfigurationViewModel"),
                    childConfigurations =
                        scriptConfigurationViewModel?.scriptConfigurationForm?.value?.childConfigurationFormSections?.mapValues {
                            it.value.getScriptConfigurationValues()
                        }
                            ?: throw RuntimeException("No scriptConfigurationViewModel"),
                    numberOfConcurrentTasks =
                        scriptConfigurationViewModel?.scriptConfigurationForm?.value?.getNumberOfConcurrentTasks()
                            ?: throw RuntimeException("No scriptConfigurationViewModel"),
                    ignoreConcurrencyConflicts = ignoreConcurrencyConflicts,
                    notificationOverrides = scriptConfigurationViewModel?.notificationOverridesForm?.toOverrides(),
                ),
            ) { result ->
                withContext(dispatcherProvider.main) {
                    when (result) {
                        is SuspendableResult.Failure -> {
                            if (result.error is NumberOfConcurrentTasksConflictException) {
                                handleNumberOfConcurrentTasksConflictException(result.error as NumberOfConcurrentTasksConflictException)
                            } else {
                                errorResolver.setError(result.error)
                            }
                        }

                        is SuspendableResult.Success -> {
                            // Script instance is added to the view in the flow observer.
                            scriptInstanceStarted.value = result.value
                        }
                    }.exhaustive
                }

                scriptLaunchLoadState.value = LoadState.NotLoading()
            }
        }
    }

    private fun handleNumberOfConcurrentTasksConflictException(exception: NumberOfConcurrentTasksConflictException) {
        startScriptWarningConfirmation.value =
            buildString {
                exception.numberOfConcurrentTasksLimited?.let {
                    appendLine(
                        "The selected '${it.definition.name}' contains fewer records (${it.numberOfRecords}) than the number of concurrent tasks. Because of this, the number of concurrent tasks will be limited to ${it.numberOfRecords}.",
                    )
                }
                exception.recordsUsedByMultipleTasks.forEach {
                    appendLine(
                        "The selected '${it.definition.name}' contains fewer records (${it.numberOfRecords}) than the number of concurrent tasks. Because of this, records in '${it.definition.name}' will be used by multiple tasks.",
                    )
                }
            }
    }

    fun onStartScriptWarningConfirmationAccepted() {
        startScriptWarningConfirmation.value = null
        launchScriptInternal(true)
    }

    fun onStartScriptWarningConfirmationDenied() {
        startScriptWarningConfirmation.value = null
    }

    fun refreshScriptList() {
        if (scriptRefreshState.value is LoadState.Loading) {
            return
        }

        scriptRefreshState.value = LoadState.Loading()

        scope.launch(dispatcherProvider.io) {
            syncScriptsOnScriptSelectionInteractor(Interactor.None()) { result ->
                when (result) {
                    is SuspendableResult.Failure -> {
                        if (result.error is ScriptSyncException) {
                            scriptRefreshState.value = LoadState.Error(result.error.toString())
                        } else {
                            scriptRefreshState.value = LoadState.Error(result.error.localizedMessage)
                        }
                    }

                    is SuspendableResult.Success -> {
                        // No-op, scripts visible in the UI should be updated through the subscribed flow.
                        scriptRefreshState.value = LoadState.NotLoading(success = true)
                    }
                }.exhaustive
            }
        }
    }

    companion object {
        // Kept in sync with R.string.script_update_required_badge / _message; resolved here because list items
        // are built off the Compose thread (consistent with other user-facing strings in this ViewModel).
        private const val UPDATE_REQUIRED_BADGE = "Update required"
        private const val UPDATE_REQUIRED_MESSAGE =
            "This script requires a newer version of Cereal. Update your client to use it."
    }
}

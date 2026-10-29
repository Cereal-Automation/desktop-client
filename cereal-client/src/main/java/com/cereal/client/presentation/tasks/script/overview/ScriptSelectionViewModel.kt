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
import com.cereal.client.application.interactor.settings.ai.HasLlmProviderConfigInteractor
import com.cereal.client.application.interactor.task.UpdateScriptPackageInstanceInteractor
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptFeature
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.entryName
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
    private val initialRequest: ScriptRequest? = null,
    /** Saves [initialScriptPackageInstance] in place instead of creating a setup; its script can't be changed. */
    val editing: Boolean = false,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val getScriptsInteractor: GetScriptsInteractor,
    private val getScriptCapacityInteractor: GetScriptCapacityInteractor,
    private val getSdkVersionInteractor: GetSdkVersionInteractor,
    private val syncScriptsOnScriptSelectionInteractor: SyncScriptsOnScriptSelectionInteractor,
    private val startScriptInteractor: StartScriptInteractor,
    private val updateScriptPackageInstanceInteractor: UpdateScriptPackageInstanceInteractor,
    private val hasNotificationChannelsConfiguredInteractor: HasNotificationChannelsConfiguredInteractor,
    private val hasLlmProviderConfigInteractor: HasLlmProviderConfigInteractor,
    private val errorResolver: ErrorResolver,
) {
    private val interactorRunner = InteractorRunner(scope, dispatcherProvider, errorResolver)

    private val scriptsListViewModel =
        GroupedListViewModel<ScriptPackage>(onSelectedItemChange = {
            updateConfig()
        })

    /** The selected package's name, heading the configuration pane; null while nothing is selected. */
    val selectedPackageName = mutableStateOf<String?>(null)

    /** The selected package's entries: its default entry first, then each variant. The picker shows when there are several. */
    val entryOptions = mutableStateOf<List<ScriptEntryOption>>(emptyList())
    val selectedEntry = mutableStateOf<ScriptEntryOption?>(null)

    /** A setup's entry is fixed once created, so while its package is selected, editing or duplicating it can't switch it. */
    val entryLocked = mutableStateOf(false)

    // One form per entry of the selected package, so switching back to an entry keeps what was typed there.
    private val configViewModels = mutableMapOf<String?, ScriptConfigurationViewModel>()

    // The requested entry to open once its package is first selected.
    private var requestedVariantId: String? = null

    val overviewViewState = mutableStateOf<ScriptSelectionState.Overview>(ScriptSelectionState.Overview.Loading)
    val configurationViewState = mutableStateOf<ScriptSelectionState.Config>(ScriptSelectionState.Config.None)
    val scriptRefreshState = mutableStateOf<LoadState>(LoadState.NotLoading())
    val scriptLaunchLoadState = mutableStateOf<LoadState>(LoadState.NotLoading())

    /** The setup once created or, when [editing], saved. */
    val scriptInstanceStarted = mutableStateOf<ScriptPackageInstance?>(null)
    val errorAction = errorResolver.errorAction
    private var scriptConfigurationViewModel: ScriptConfigurationViewModel? = null

    // The initial script is auto-selected once; later emissions (refresh, install) must not override
    // the user's own selection or discard their edited configuration.
    private var initialSelectionApplied = false
    val startScriptWarningConfirmation = mutableStateOf<String?>(null)
    val showNoNotificationChannelsWarning = mutableStateOf(false)
    val showNoAiProviderWarning = mutableStateOf(false)

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
                            // Pick the duplicated instance's or requested script once; otherwise default to the first.
                            val initialEntry =
                                initialScriptPackageInstance?.let { ScriptRequest(it.definition.manifest.packageName, it.variantId) }
                                    ?: initialRequest
                            val initialScript =
                                initialEntry?.takeUnless { initialSelectionApplied }?.let { entry ->
                                    items.find { item -> item.id.manifest.packageName == entry.packageName }
                                }
                            scriptsListViewModel.updateItems(items, selectFirstWhenNoSelection = initialScript == null)
                            initialScript?.let { scriptItem ->
                                initialSelectionApplied = true
                                requestedVariantId = initialEntry.variantId?.takeIf { it in scriptItem.id.variants }
                                scriptsListViewModel.selectItem(scriptItem.id)
                            }

                            // A single installed script needs no choosing: hide the list.
                            overviewViewState.value =
                                if (items.size == 1) {
                                    ScriptSelectionState.Overview.Single
                                } else {
                                    ScriptSelectionState.Overview.Filled(scriptsListViewModel)
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
        configViewModels.clear()
        scriptsListViewModel.selectedItem?.let { selectedScript ->
            val options = (listOf(null) + selectedScript.variants.keys).map { ScriptEntryOption(it, selectedScript.entryName(it)) }
            selectedPackageName.value = selectedScript.manifest.name
            entryOptions.value = options
            // The edited or duplicated setup's package always reopens on its own entry; a request applies once.
            val fixedEntry =
                initialScriptPackageInstance?.takeIf { it.definition.manifest.packageName == selectedScript.manifest.packageName }
            entryLocked.value = fixedEntry != null
            val variantId = fixedEntry?.variantId ?: requestedVariantId
            requestedVariantId = null
            selectEntry(options.firstOrNull { it.variantId == variantId } ?: options.first())
            loadSelectedScriptCapacity(selectedScript)
        } ?: run {
            selectedPackageName.value = null
            entryLocked.value = false
            entryOptions.value = emptyList()
            selectedEntry.value = null
            scriptConfigurationViewModel = null
            configurationViewState.value = ScriptSelectionState.Config.None
        }
    }

    /** Opens [option]'s form within the selected package, reusing the one already filled in for it. */
    fun selectEntry(option: ScriptEntryOption) {
        val selectedScript = scriptsListViewModel.selectedItem ?: return
        selectedEntry.value = option
        val configViewModel =
            configViewModels.getOrPut(option.variantId) {
                // Only pass initial instance if the selected entry matches the initial instance's entry.
                val initialInstance =
                    initialScriptPackageInstance?.takeIf {
                        it.definition.manifest.packageName == selectedScript.manifest.packageName && it.variantId == option.variantId
                    }
                KoinJavaComponent.get(
                    ScriptConfigurationViewModel::class.java,
                    parameters = { parametersOf(scope, selectedScript, initialInstance, option.variantId) },
                )
            }
        scriptConfigurationViewModel = configViewModel
        configurationViewState.value = ScriptSelectionState.Config.Selected(configViewModel)
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

    /** The form's main and child configuration values; null until a script's form is loaded. */
    private fun configuredValues(): Pair<ScriptConfigurationValues, Map<String, ScriptConfigurationValues>>? {
        val form = scriptConfigurationViewModel?.scriptConfigurationForm?.value ?: return null
        return form.mainConfigurationFormSection.getScriptConfigurationValues() to
            form.childConfigurationFormSections.mapValues { it.value.getScriptConfigurationValues() }
    }

    /** Saves the edited setup in place; a concurrency conflict asks for confirmation first, as creation does. */
    fun saveEdit() = launchScriptInternal(false)

    /**
     * Runs the pre-launch checks, then starts the script. The AI provider check comes first because a
     * missing provider breaks the script; the notification check follows.
     */
    fun launchScript() {
        if (scriptsListViewModel.selectedItem
                ?.manifest
                ?.declares(ScriptFeature.LLM) != true
        ) {
            checkNotificationChannelsThenLaunch()
            return
        }
        interactorRunner.launch(hasLlmProviderConfigInteractor, Interactor.None()) { hasProvider ->
            if (hasProvider) checkNotificationChannelsThenLaunch() else showNoAiProviderWarning.value = true
        }
    }

    fun onNoAiProviderWarningAccepted() {
        showNoAiProviderWarning.value = false
        checkNotificationChannelsThenLaunch()
    }

    fun onNoAiProviderWarningDismissed() {
        showNoAiProviderWarning.value = false
    }

    private fun checkNotificationChannelsThenLaunch() {
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
        val (mainConfiguration, childConfigurations) = configuredValues() ?: throw RuntimeException("No scriptConfigurationViewModel")
        val notificationOverrides = scriptConfigurationViewModel?.notificationOverridesForm?.toOverrides()
        val onResult: suspend (SuspendableResult<ScriptPackageInstance, Exception>) -> Unit = { result ->
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

        scope.launch(dispatcherProvider.io) {
            val edited = initialScriptPackageInstance?.takeIf { editing }
            if (edited != null) {
                updateScriptPackageInstanceInteractor(
                    UpdateScriptPackageInstanceInteractor.Params(
                        edited.copy(
                            mainConfiguration = mainConfiguration,
                            childConfigurations = childConfigurations,
                            notificationOverrides = notificationOverrides,
                        ),
                        ignoreConcurrencyConflicts,
                    ),
                    onResult,
                )
            } else {
                startScriptInteractor(
                    StartScriptInteractor.Params(
                        groupId = scriptPackageGroup.id,
                        scriptPackage = scriptsListViewModel.selectedItem!!,
                        variantId = selectedEntry.value?.variantId,
                        mainScriptConfiguration = mainConfiguration,
                        childConfigurations = childConfigurations,
                        // The header's stepper sets the count after creation; a duplicate keeps its source's.
                        numberOfConcurrentTasks =
                            scriptConfigurationViewModel?.initialScriptPackageInstance?.numberOfConcurrentTasks ?: 1,
                        ignoreConcurrencyConflicts = ignoreConcurrencyConflicts,
                        notificationOverrides = notificationOverrides,
                    ),
                    onResult,
                )
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

/** One entry of the selected package in the picker: its default entry ([variantId] null) or one of its variants. */
data class ScriptEntryOption(
    val variantId: String?,
    val name: String,
)

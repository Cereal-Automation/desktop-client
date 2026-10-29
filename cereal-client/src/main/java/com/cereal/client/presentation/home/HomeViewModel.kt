package com.cereal.client.presentation.home

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.agent.isAiBrowserAgent
import com.cereal.client.application.interactor.agent.GoalRunFormPrefill
import com.cereal.client.application.interactor.agent.NoNotificationChannelsException
import com.cereal.client.application.interactor.agent.PrefillGoalRunFormInteractor
import com.cereal.client.application.interactor.agent.StartGoalRunInteractor
import com.cereal.client.application.interactor.script.GetScriptConfigDefinitionInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.script.GetSdkVersionInteractor
import com.cereal.client.application.interactor.task.StartAllTasksInScriptPackageInstanceInteractor
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.isClientUpdateRequired
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.error.handleFailureOrElse
import com.cereal.client.presentation.tasks.script.overview.configuration.RawScriptConfigValues
import com.cereal.client.presentation.tasks.script.overview.configuration.model.ConfigurationItemsFormSection
import com.cereal.client.presentation.tasks.script.overview.configuration.validate
import com.cereal.client.presentation.view.fields.state.FormFieldState
import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.Visibility
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.swiftzer.semver.SemVer

/** What fills the composer slot on Home: the first state in [homeComposerSlot]'s order that matches. */
sealed interface HomeComposerSlot {
    /** The installed scripts or the AI provider aren't read yet: nothing shows, so no wrong card flashes. */
    data object Loading : HomeComposerSlot

    data object NotInstalled : HomeComposerSlot

    data object UpdateRequired : HomeComposerSlot

    data object Composer : HomeComposerSlot

    /** No AI provider configured: the "connect an AI model" card, over a greyed-out composer. */
    data object NoAiProvider : HomeComposerSlot
}

/** Ordered checks; the first match wins. */
fun homeComposerSlot(
    agent: ScriptPackage?,
    sdkVersion: SemVer,
    hasAiProvider: Boolean,
): HomeComposerSlot =
    when {
        agent == null -> HomeComposerSlot.NotInstalled
        agent.isClientUpdateRequired(sdkVersion) -> HomeComposerSlot.UpdateRequired
        !hasAiProvider -> HomeComposerSlot.NoAiProvider
        else -> HomeComposerSlot.Composer
    }

/**
 * Whether Home shows this field: one the user has to fill in. That is a field the script marks
 * [Visibility.VisibleRequired] right now, or a non-null one without a default; a field with a default
 * stays behind More options.
 */
fun ScriptConfigurationItemDefinition.isShownOnHome(config: ScriptConfig): Boolean =
    when (stateModifier?.getVisibility(config)) {
        Visibility.Hidden -> false
        Visibility.VisibleRequired -> true
        else -> !isNullable && defaultValue == null
    }

/** Field types Home renders inline; lists, files, proxies and datasets need the generic form's dialogs. */
private val ScriptConfigurationItemDefinition.isRenderableOnHome: Boolean
    get() =
        when (type) {
            ConfigItemType.StringConfigItem,
            ConfigItemType.SecretConfigItem,
            ConfigItemType.IntConfigItem,
            ConfigItemType.FloatConfigItem,
            ConfigItemType.DoubleConfigItem,
            ConfigItemType.BooleanConfigItem,
            is ConfigItemType.EnumConfigItem,
            -> true

            else -> false
        }

sealed interface HomeDialog {
    data object NoNotificationChannelsWarning : HomeDialog

    /**
     * The agent's generic script config form, prefilled from the composer. [startOnCreate] when Start fell
     * back to it, so launching the form still leaves a running goal run.
     */
    data class MoreOptions(
        val prefill: GoalRunFormPrefill,
        val startOnCreate: Boolean,
    ) : HomeDialog
}

/** Home's composer slot and the goal composer. The AI setup card and the install card have their own ViewModels. */
class HomeViewModel(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    getScriptsInteractor: GetScriptsInteractor,
    getSdkVersionInteractor: GetSdkVersionInteractor,
    private val getScriptConfigDefinitionInteractor: GetScriptConfigDefinitionInteractor,
    private val prefillGoalRunFormInteractor: PrefillGoalRunFormInteractor,
    private val startGoalRunInteractor: StartGoalRunInteractor,
    private val startAllTasksInScriptPackageInstanceInteractor: StartAllTasksInScriptPackageInstanceInteractor,
    private val errorResolver: ErrorResolver,
) {
    private val _slot = mutableStateOf<HomeComposerSlot>(HomeComposerSlot.Loading)
    val slot: State<HomeComposerSlot> = _slot

    /** The agent's main-script form: Home renders [composerFields] of it. Null until the definition loads. */
    private val _composerForm = mutableStateOf<ConfigurationItemsFormSection?>(null)
    val composerForm: State<ConfigurationItemsFormSection?> = _composerForm

    val dialog = mutableStateOf<HomeDialog?>(null)
    val isStarting = mutableStateOf(false)
    val errorAction = errorResolver.errorAction

    private var agent: ScriptPackage? = null

    /** Set once the installed scripts have loaded; the slot stays [HomeComposerSlot.Loading] until then. */
    private var clientSdk: SemVer? = null

    /** Whether an AI provider is configured; null until known, which keeps the slot [HomeComposerSlot.Loading]. */
    private var hasAiProvider: Boolean? = null

    init {
        scope.launch(dispatcherProvider.io) {
            var sdkVersion: SemVer? = null
            getSdkVersionInteractor(Interactor.None()) { result -> sdkVersion = (result as? SuspendableResult.Success)?.value }
            val clientSdk = sdkVersion ?: return@launch
            getScriptsInteractor(Interactor.None()).collectLatest { result ->
                withContext(dispatcherProvider.main) {
                    if (result is SuspendableResult.Success) {
                        val installed = result.value.firstOrNull { it.isAiBrowserAgent }
                        if (installed != null && installed.manifest.versionCode != agent?.manifest?.versionCode) loadComposerForm(installed)
                        agent = installed
                        this@HomeViewModel.clientSdk = clientSdk
                        updateSlot()
                    }
                }
            }
        }
    }

    /** Fed from Home's AI provider state: whether a provider is configured now. */
    fun onAiProviderChanged(configured: Boolean) {
        hasAiProvider = configured
        updateSlot()
    }

    private fun updateSlot() {
        val sdk = clientSdk ?: return
        val hasAiProvider = hasAiProvider ?: return
        _slot.value = homeComposerSlot(agent, sdk, hasAiProvider)
    }

    private fun loadComposerForm(agent: ScriptPackage) {
        scope.launch(dispatcherProvider.io) {
            getScriptConfigDefinitionInteractor(GetScriptConfigDefinitionInteractor.Params(agent, scriptPackageInstance = null)) { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) { definition ->
                        // Home renders no field that imports a file, so there's nothing to open an import for.
                        _composerForm.value =
                            ConfigurationItemsFormSection(definition.mainConfiguration) { _, _, _ -> }.apply { applyStateModifiers() }
                    }
                }
            }
        }
    }

    /** The fields Home has to show, in the script's order, rendered or not. Reads the form's state, so it follows edits. */
    private fun requiredFields(): List<Pair<ConfigurationItem, FormFieldState<*, *>>> {
        val form = _composerForm.value ?: return emptyList()
        val config = RawScriptConfigValues(form.getScriptConfigurationValues())
        return form.configurationItemToFieldState.entries
            .filter { it.key.definition.isShownOnHome(config) }
            .sortedBy { it.key.definition.position }
            .map { it.toPair() }
    }

    /** What the composer renders: the required fields of a type Home can show. */
    fun composerFields(): List<Pair<ConfigurationItem, FormFieldState<*, *>>> = requiredFields().filter { (item, _) -> item.definition.isRenderableOnHome }

    /** Only the live composer starts runs: never the greyed one under the AI setup card. */
    private fun canStart(): Boolean = _slot.value == HomeComposerSlot.Composer && hasAiProvider == true

    fun onStart() {
        val agent = agent ?: return
        val form = _composerForm.value ?: return
        if (!canStart()) return
        val required = requiredFields()
        val rendered = required.filter { (item, _) -> item.definition.isRenderableOnHome }
        // Validate every shown field (no short-circuit), so each one shows its own error.
        if (!rendered.map { (_, state) -> state.validate() }.all { it }) return
        // A required field Home can't render, or an invalid one it doesn't show: the generic form shows every
        // field, so let the user fix it there.
        val values = form.getScriptConfigurationValues()
        val shown = rendered.map { (item, _) -> item.definition.key }.toSet()
        val unrendered = required.size != rendered.size
        val invalidHidden =
            agent.mainScript.configuration
                .validate(values)
                .any { it.key !in shown }
        if (unrendered || invalidHidden) {
            openMoreOptions(agent, startOnCreate = true)
            return
        }
        startGoalRun(agent, ignoreMissingNotificationChannels = false)
    }

    fun onNoNotificationChannelsWarningAccepted() {
        dialog.value = null
        agent?.let { startGoalRun(it, ignoreMissingNotificationChannels = true) }
    }

    fun onDialogDismissed() {
        dialog.value = null
    }

    fun onMoreOptions() {
        val agent = agent ?: return
        if (!canStart()) return
        openMoreOptions(agent, startOnCreate = false)
    }

    /** The generic form created [instance]; when Start fell back to the form, run it right away. */
    fun onMoreOptionsInstanceCreated(instance: ScriptPackageInstance) {
        val startOnCreate = (dialog.value as? HomeDialog.MoreOptions)?.startOnCreate == true
        if (!startOnCreate) return
        scope.launch(dispatcherProvider.io) {
            startAllTasksInScriptPackageInstanceInteractor(StartAllTasksInScriptPackageInstanceInteractor.Params(instance)) { result ->
                withContext(dispatcherProvider.main) { result.handleFailureOrElse(errorResolver) { } }
            }
        }
    }

    private fun openMoreOptions(
        agent: ScriptPackage,
        startOnCreate: Boolean,
    ) {
        val configuration = configuration() ?: return
        scope.launch(dispatcherProvider.io) {
            prefillGoalRunFormInteractor(PrefillGoalRunFormInteractor.Params(agent, configuration)) { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) { prefill ->
                        dialog.value = HomeDialog.MoreOptions(prefill, startOnCreate)
                    }
                }
            }
        }
    }

    private fun startGoalRun(
        agent: ScriptPackage,
        ignoreMissingNotificationChannels: Boolean,
    ) {
        val configuration = configuration() ?: return
        isStarting.value = true
        scope.launch(dispatcherProvider.io) {
            startGoalRunInteractor(StartGoalRunInteractor.Params(agent, configuration, ignoreMissingNotificationChannels)) { result ->
                withContext(dispatcherProvider.main) {
                    isStarting.value = false
                    // The composer keeps its text so the user can tweak it and run again.
                    if (result is SuspendableResult.Failure && result.error is NoNotificationChannelsException) {
                        dialog.value = HomeDialog.NoNotificationChannelsWarning
                    } else {
                        result.handleFailureOrElse(errorResolver) { }
                    }
                }
            }
        }
    }

    /** Every value of the form: what the composer shows plus the defaults it keeps. */
    private fun configuration(): ScriptConfigurationValues? = _composerForm.value?.getScriptConfigurationValues()
}

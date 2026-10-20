package com.cereal.client.presentation.home

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.agent.GoalRunInput
import com.cereal.client.application.agent.isAiBrowserAgent
import com.cereal.client.application.agent.supportsGoalComposer
import com.cereal.client.application.interactor.agent.GoalRunFormPrefill
import com.cereal.client.application.interactor.agent.NoNotificationChannelsException
import com.cereal.client.application.interactor.agent.PrefillGoalRunFormInteractor
import com.cereal.client.application.interactor.agent.StartGoalRunInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.script.GetSdkVersionInteractor
import com.cereal.client.application.interactor.task.StartAllTasksInScriptPackageInstanceInteractor
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.itemForKey
import com.cereal.client.domain.model.script.isClientUpdateRequired
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.error.handleFailureOrElse
import com.cereal.client.presentation.tasks.script.overview.configuration.RawScriptConfigValues
import com.cereal.client.presentation.tasks.script.overview.configuration.validate
import com.cereal.client.presentation.view.UiText
import com.cereal.client.presentation.view.fields.validator.isRequired
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.home_field_required
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.swiftzer.semver.SemVer

/** What fills the composer slot on Home: the first state in [homeComposerSlot]'s order that matches. */
sealed interface HomeComposerSlot {
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

/** The composer's fields, for routing validation messages. */
enum class ComposerField(
    val key: String,
) {
    GOAL(GoalRunInput.KEY_GOAL),
    START_URL(GoalRunInput.KEY_START_URL),
    CHECK_EVERY(GoalRunInput.KEY_REPEAT_INTERVAL),
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
    private val prefillGoalRunFormInteractor: PrefillGoalRunFormInteractor,
    private val startGoalRunInteractor: StartGoalRunInteractor,
    private val startAllTasksInScriptPackageInstanceInteractor: StartAllTasksInScriptPackageInstanceInteractor,
    private val errorResolver: ErrorResolver,
) {
    private val _slot = mutableStateOf<HomeComposerSlot>(HomeComposerSlot.Composer)
    val slot: State<HomeComposerSlot> = _slot

    val goal = mutableStateOf("")
    val startUrl = mutableStateOf("")

    /** Whether the installed agent requires a start page; Home labels the field "Optional" otherwise. */
    val startUrlRequired = mutableStateOf(false)
    val repeat = mutableStateOf(false)
    val checkEveryMinutes = mutableStateOf(DEFAULT_CHECK_EVERY_MINUTES.toString())

    /** Validation messages from the script's own validators, by field. */
    val fieldErrors = mutableStateOf<Map<ComposerField, UiText>>(emptyMap())
    val dialog = mutableStateOf<HomeDialog?>(null)
    val isStarting = mutableStateOf(false)
    val errorAction = errorResolver.errorAction

    private var agent: ScriptPackage? = null

    /** Set once the installed scripts have loaded; the slot stays on the composer until then. */
    private var clientSdk: SemVer? = null

    /** Whether an AI provider is configured; null until known, which keeps the composer so the setup card doesn't flash. */
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
                        if (agent == null && installed != null) applyDefaults(installed)
                        agent = installed
                        startUrlRequired.value = installed?.let(::isStartUrlRequired) == true
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
        _slot.value = homeComposerSlot(agent, sdk, hasAiProvider = hasAiProvider != false)
    }

    private fun isStartUrlRequired(agent: ScriptPackage): Boolean {
        val definition = agent.mainScript.configuration
        val values = RawScriptConfigValues(input().toConfiguration(definition))
        return definition.itemForKey(GoalRunInput.KEY_START_URL)?.isRequired(values) == true
    }

    private fun applyDefaults(agent: ScriptPackage) {
        val default =
            agent.mainScript.configuration
                .itemForKey(GoalRunInput.KEY_REPEAT_INTERVAL)
                ?.defaultValue as? ConfigValue.IntValue
        default?.let { checkEveryMinutes.value = (it.raw / SECONDS_PER_MINUTE).toString() }
    }

    fun onGoalChange(value: String) {
        goal.value = value
    }

    fun onStartUrlChange(value: String) {
        startUrl.value = value
    }

    fun onRepeatChange(value: Boolean) {
        repeat.value = value
    }

    fun onCheckEveryMinutesChange(value: String) {
        checkEveryMinutes.value = value
    }

    /** Only the live composer starts runs: never the greyed one under the AI setup card. */
    private fun canStart(): Boolean = _slot.value == HomeComposerSlot.Composer && hasAiProvider == true

    fun onStart() {
        val agent = agent ?: return
        if (!canStart()) return
        if (!agent.mainScript.configuration.supportsGoalComposer()) {
            openMoreOptions(agent, startOnCreate = true)
            return
        }
        if (!validate(agent)) return
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
        scope.launch(dispatcherProvider.io) {
            prefillGoalRunFormInteractor(PrefillGoalRunFormInteractor.Params(agent, input())) { result ->
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
        isStarting.value = true
        scope.launch(dispatcherProvider.io) {
            startGoalRunInteractor(StartGoalRunInteractor.Params(agent, input(), ignoreMissingNotificationChannels)) { result ->
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

    /** Runs the script's own validators over the composer's keys; shows their messages under the visible fields. */
    private fun validate(agent: ScriptPackage): Boolean {
        val definition = agent.mainScript.configuration
        val values = input().toConfiguration(definition)
        val visible = if (repeat.value) ComposerField.entries else ComposerField.entries - ComposerField.CHECK_EVERY
        val invalid = definition.validate(values).associateBy { it.key }
        fieldErrors.value =
            visible
                .mapNotNull { field ->
                    invalid[field.key]?.let { item ->
                        val message = item.stateModifier?.getError(RawScriptConfigValues(values))
                        field to (message?.let { UiText.Raw(it) } ?: UiText.Resource(Res.string.home_field_required))
                    }
                }.toMap()
        if (fieldErrors.value.isNotEmpty()) return false
        // An item the composer doesn't show is invalid (e.g. an agent build that still wants its own API key):
        // the generic form shows every field, so let the user fix it there.
        if (invalid.isNotEmpty()) {
            openMoreOptions(agent, startOnCreate = true)
            return false
        }
        return true
    }

    private fun input() =
        GoalRunInput(
            goal = goal.value.trim(),
            startUrl = startUrl.value.trim(),
            repeat = repeat.value,
            repeatIntervalSeconds = (checkEveryMinutes.value.trim().toIntOrNull() ?: 0) * SECONDS_PER_MINUTE,
        )

    private companion object {
        const val SECONDS_PER_MINUTE = 60
        const val DEFAULT_CHECK_EVERY_MINUTES = 5
    }
}

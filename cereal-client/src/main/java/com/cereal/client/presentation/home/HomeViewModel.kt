package com.cereal.client.presentation.home

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.agent.GetAiBrowserAgentListingInteractor
import com.cereal.client.application.interactor.agent.InstallAiBrowserAgentInteractor
import com.cereal.client.application.interactor.agent.StartGoalRunInteractor
import com.cereal.client.application.interactor.notification.HasNotificationChannelsConfiguredInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.script.GetSdkVersionInteractor
import com.cereal.client.application.interactor.task.GetOrCreateDefaultTaskGroupInteractor
import com.cereal.client.domain.model.agent.AI_BROWSER_AGENT_PACKAGE
import com.cereal.client.domain.model.agent.GoalRunInput
import com.cereal.client.domain.model.agent.supportsGoalComposer
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.itemForKey
import com.cereal.client.domain.model.script.isClientUpdateRequired
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.error.handleFailureOrElse
import com.cereal.client.presentation.tasks.script.overview.configuration.RawScriptConfigValues
import com.cereal.client.presentation.tasks.script.overview.configuration.validate
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.swiftzer.semver.SemVer
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** What fills the composer slot on Home: the first state in [homeComposerSlot]'s order that matches. */
sealed interface HomeComposerSlot {
    data object NotInstalled : HomeComposerSlot

    data object UpdateRequired : HomeComposerSlot

    data object Composer : HomeComposerSlot
}

/** Ordered checks; the first match wins. */
fun homeComposerSlot(
    agent: ScriptPackage?,
    sdkVersion: SemVer,
): HomeComposerSlot =
    when {
        agent == null -> HomeComposerSlot.NotInstalled
        agent.isClientUpdateRequired(sdkVersion) -> HomeComposerSlot.UpdateRequired
        else -> HomeComposerSlot.Composer
    }

sealed interface AgentInstallState {
    data object Idle : AgentInstallState

    data object Installing : AgentInstallState

    data class Failed(
        val message: String,
    ) : AgentInstallState
}

sealed interface HomeDialog {
    data object NoNotificationChannelsWarning : HomeDialog

    /** The agent's generic script config form, prefilled from the composer. */
    data class MoreOptions(
        val group: ScriptPackageGroup,
        val prefill: ScriptPackageInstance,
    ) : HomeDialog
}

// Constructor parameters are injected dependencies (interactors and the error resolver).
@Suppress("LongParameterList")
class HomeViewModel(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    getScriptsInteractor: GetScriptsInteractor,
    getSdkVersionInteractor: GetSdkVersionInteractor,
    getAiBrowserAgentListingInteractor: GetAiBrowserAgentListingInteractor,
    private val installAiBrowserAgentInteractor: InstallAiBrowserAgentInteractor,
    private val hasNotificationChannelsConfiguredInteractor: HasNotificationChannelsConfiguredInteractor,
    private val getOrCreateDefaultTaskGroupInteractor: GetOrCreateDefaultTaskGroupInteractor,
    private val startGoalRunInteractor: StartGoalRunInteractor,
    private val errorResolver: ErrorResolver,
) {
    private val _slot = mutableStateOf<HomeComposerSlot>(HomeComposerSlot.Composer)
    val slot: State<HomeComposerSlot> = _slot

    val installState = mutableStateOf<AgentInstallState>(AgentInstallState.Idle)

    /** The agent's marketplace listing is in maintenance mode: a non-blocking banner over the composer. */
    val isUnderMaintenance = mutableStateOf(false)

    val goal = mutableStateOf("")
    val startUrl = mutableStateOf("")
    val repeat = mutableStateOf(false)
    val checkEveryMinutes = mutableStateOf(DEFAULT_CHECK_EVERY_MINUTES.toString())

    /** Validation messages from the script's own validators, by config key. */
    val fieldErrors = mutableStateOf<Map<String, String>>(emptyMap())
    val dialog = mutableStateOf<HomeDialog?>(null)
    val isStarting = mutableStateOf(false)
    val errorAction = errorResolver.errorAction

    private var agent: ScriptPackage? = null

    init {
        scope.launch(dispatcherProvider.io) {
            var sdkVersion: SemVer? = null
            getSdkVersionInteractor(Interactor.None()) { result -> sdkVersion = (result as? SuspendableResult.Success)?.value }
            val clientSdk = sdkVersion ?: return@launch
            getScriptsInteractor(Interactor.None()).collectLatest { result ->
                withContext(dispatcherProvider.main) {
                    if (result is SuspendableResult.Success) {
                        val installed = result.value.firstOrNull { it.manifest.packageName == AI_BROWSER_AGENT_PACKAGE }
                        if (agent == null && installed != null) applyDefaults(installed)
                        agent = installed
                        _slot.value = homeComposerSlot(installed, clientSdk)
                        if (installed != null) installState.value = AgentInstallState.Idle
                    }
                }
            }
        }
        scope.launch(dispatcherProvider.io) {
            // A failed lookup just means no banner; Install surfaces lookup errors itself.
            getAiBrowserAgentListingInteractor(Interactor.None()) { result ->
                if (result is SuspendableResult.Success) {
                    withContext(dispatcherProvider.main) { isUnderMaintenance.value = result.value.maintenanceMode }
                }
            }
        }
    }

    /** Runs the normal marketplace install; the composer appears once the installed agent shows up. */
    fun onInstall() {
        installState.value = AgentInstallState.Installing
        scope.launch(dispatcherProvider.io) {
            installAiBrowserAgentInteractor(Interactor.None()) { result ->
                withContext(dispatcherProvider.main) {
                    if (result is SuspendableResult.Failure) {
                        installState.value = AgentInstallState.Failed(result.error.localizedMessage ?: INSTALL_FAILED_MESSAGE)
                    }
                }
            }
        }
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

    fun onStart() {
        val agent = agent ?: return
        if (!agent.mainScript.configuration.supportsGoalComposer()) {
            onMoreOptions()
            return
        }
        if (!validate(agent)) return

        scope.launch(dispatcherProvider.io) {
            hasNotificationChannelsConfiguredInteractor(HasNotificationChannelsConfiguredInteractor.Params(null)) { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) { configured ->
                        if (configured) startGoalRun(agent) else dialog.value = HomeDialog.NoNotificationChannelsWarning
                    }
                }
            }
        }
    }

    fun onNoNotificationChannelsWarningAccepted() {
        dialog.value = null
        agent?.let { startGoalRun(it) }
    }

    fun onDialogDismissed() {
        dialog.value = null
    }

    @OptIn(ExperimentalTime::class)
    fun onMoreOptions() {
        val agent = agent ?: return
        scope.launch(dispatcherProvider.io) {
            getOrCreateDefaultTaskGroupInteractor(Interactor.None()) { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) { group ->
                        val prefill =
                            ScriptPackageInstance(
                                id = UUID.randomUUID().toString(),
                                mainConfiguration = input().toConfiguration(agent.mainScript.configuration),
                                childConfigurations = emptyMap(),
                                definition = agent,
                                createdAt = Clock.System.now(),
                                numberOfConcurrentTasks = 1,
                            )
                        dialog.value = HomeDialog.MoreOptions(group, prefill)
                    }
                }
            }
        }
    }

    private fun startGoalRun(agent: ScriptPackage) {
        isStarting.value = true
        scope.launch(dispatcherProvider.io) {
            startGoalRunInteractor(StartGoalRunInteractor.Params(agent, input())) { result ->
                withContext(dispatcherProvider.main) {
                    isStarting.value = false
                    // The composer keeps its text so the user can tweak it and run again.
                    result.handleFailureOrElse(errorResolver) { }
                }
            }
        }
    }

    /** Runs the script's own validators over the composer's keys; shows their messages under the visible fields. */
    private fun validate(agent: ScriptPackage): Boolean {
        val definition = agent.mainScript.configuration
        val values = input().toConfiguration(definition)
        val visibleKeys = if (repeat.value) GoalRunInput.KEYS else GoalRunInput.KEYS - GoalRunInput.KEY_REPEAT_INTERVAL
        fieldErrors.value =
            definition
                .validate(values)
                .filter { it.key in visibleKeys }
                .associate { it.key to (it.stateModifier?.getError(RawScriptConfigValues(values)) ?: REQUIRED_MESSAGE) }
        return fieldErrors.value.isEmpty()
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
        const val REQUIRED_MESSAGE = "This field is required."
        const val INSTALL_FAILED_MESSAGE = "The install failed."
    }
}

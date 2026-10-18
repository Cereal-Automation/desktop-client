package com.cereal.client.presentation.home

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.task.ObserveTasksInteractor
import com.cereal.client.domain.model.agent.AI_BROWSER_AGENT_PACKAGE
import com.cereal.client.domain.model.agent.GoalRunInput
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.task.Task
import com.cereal.client.presentation.tasks.mappers.toDisplayStatus
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

data class RecentRunUiModel(
    val taskId: String,
    val status: String,
    val goal: String,
    val site: String,
    val mode: String,
    /** Latest "Cost: $x" the agent reported in a status message; null until it reports one. */
    val cost: String?,
)

data class ReadyMadeScriptUiModel(
    val packageName: String,
    val name: String,
)

/** The two sections below Home's composer slot: recent goal runs and the ready-made scripts strip. */
class HomeActivityViewModel(
    scope: CoroutineScope,
    dispatcherProvider: CoroutinesDispatcherProvider,
    observeTasksInteractor: ObserveTasksInteractor,
    getScriptsInteractor: GetScriptsInteractor,
) {
    private val _recentRuns = mutableStateOf<List<RecentRunUiModel>>(emptyList())
    val recentRuns: State<List<RecentRunUiModel>> = _recentRuns

    private val _readyMadeScripts = mutableStateOf<List<ReadyMadeScriptUiModel>>(emptyList())
    val readyMadeScripts: State<List<ReadyMadeScriptUiModel>> = _readyMadeScripts

    init {
        scope.launch(dispatcherProvider.io) {
            observeTasksInteractor(Interactor.None()).collectLatest { result ->
                if (result is SuspendableResult.Success) {
                    val runs =
                        result.value
                            .filter { it.scriptInstance.packageInstance.definition.manifest.packageName == AI_BROWSER_AGENT_PACKAGE }
                            .sortedByDescending { it.createdAt }
                            .take(RECENT_RUNS)
                            .map { it.toRecentRun() }
                    withContext(dispatcherProvider.main) { _recentRuns.value = runs }
                }
            }
        }
        scope.launch(dispatcherProvider.io) {
            getScriptsInteractor(Interactor.None()).collectLatest { result ->
                if (result is SuspendableResult.Success) {
                    val scripts =
                        result.value
                            .filter { it.manifest.packageName != AI_BROWSER_AGENT_PACKAGE }
                            .map { ReadyMadeScriptUiModel(it.manifest.packageName, it.manifest.name) }
                    withContext(dispatcherProvider.main) { _readyMadeScripts.value = scripts }
                }
            }
        }
    }

    private fun Task.toRecentRun(): RecentRunUiModel {
        val config = scriptInstance.packageInstance.mainConfiguration
        val startUrl = (config[GoalRunInput.KEY_START_URL] as? ConfigValue.StringValue)?.raw.orEmpty()
        return RecentRunUiModel(
            taskId = id,
            status = status.toDisplayStatus(),
            goal = (config[GoalRunInput.KEY_GOAL] as? ConfigValue.StringValue)?.raw.orEmpty(),
            site = runCatching { URI(startUrl).host }.getOrNull() ?: startUrl,
            mode = (config[GoalRunInput.KEY_RUN_MODE] as? ConfigValue.EnumValue)?.raw?.name.orEmpty(),
            cost = statusHistory.asReversed().firstNotNullOfOrNull { s -> s.message?.let { COST.find(it)?.groupValues?.get(1) } },
        )
    }

    private companion object {
        const val RECENT_RUNS = 3

        // ponytail: the agent has no structured cost; parse its "Cost: $x" status text until it reports one.
        val COST = Regex("""Cost: (\$\d+(?:\.\d+)?)""")
    }
}

package com.cereal.client.presentation.home

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.agent.goalRunDetails
import com.cereal.client.application.agent.isAiBrowserAgent
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.task.ObserveTasksInteractor
import com.cereal.client.domain.model.script.entryIds
import com.cereal.client.domain.model.script.entryName
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
    /** The variant this entry runs; null for a package's default entry. */
    val variantId: String? = null,
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
                            .filter { it.scriptInstance.packageInstance.definition.isAiBrowserAgent }
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
                        result.value.flatMap { pkg ->
                            pkg.entryIds.map { id -> ReadyMadeScriptUiModel(pkg.manifest.packageName, pkg.entryName(id), id) }
                        }
                    withContext(dispatcherProvider.main) { _readyMadeScripts.value = scripts }
                }
            }
        }
    }

    private fun Task.toRecentRun(): RecentRunUiModel {
        val details = goalRunDetails()
        return RecentRunUiModel(
            taskId = id,
            status = status.toDisplayStatus(),
            // Only the goal composer's entry has a goal; the agent's other entries are titled by their name.
            goal = details.goal.ifEmpty { scriptInstance.packageInstance.entryName },
            site = runCatching { URI(details.startUrl).host }.getOrNull() ?: details.startUrl,
            mode = details.mode,
            cost = details.cost,
        )
    }

    private companion object {
        const val RECENT_RUNS = 3
    }
}

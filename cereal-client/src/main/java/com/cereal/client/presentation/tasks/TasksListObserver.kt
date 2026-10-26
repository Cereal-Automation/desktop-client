package com.cereal.client.presentation.tasks

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.script.GetScriptsInGroupInteractor
import com.cereal.client.application.interactor.task.GetTaskGroupsInteractor
import com.cereal.client.application.interactor.task.ObserveTasksInteractor
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.Task
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.error.handleFailureOrElse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TasksListObserver(
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val errorResolver: ErrorResolver,
    private val getTaskGroupsInteractor: GetTaskGroupsInteractor,
    private val observeTasksInteractor: ObserveTasksInteractor,
    private val getScriptsInGroupInteractor: GetScriptsInGroupInteractor,
) {
    private var getScriptsInGroupInteractorJob: Job? = null

    /**
     * Every group with its task setups, in group order. Reported only once every group's setups have loaded, so the
     * list never shows a group without its setups (nor the empty state ahead of the list).
     */
    fun observeTaskGroups(
        scope: CoroutineScope,
        onGroupsChanged: (List<Pair<ScriptPackageGroup, List<ScriptPackageInstance>>>) -> Unit,
    ) {
        scope.launch(dispatcherProvider.io) {
            getTaskGroupsInteractor(Interactor.None()).collectLatest { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) { groups -> observeSetups(scope, groups, onGroupsChanged) }
                }
            }
        }
    }

    private fun observeSetups(
        scope: CoroutineScope,
        groups: List<ScriptPackageGroup>,
        onGroupsChanged: (List<Pair<ScriptPackageGroup, List<ScriptPackageInstance>>>) -> Unit,
    ) {
        getScriptsInGroupInteractorJob?.cancel()
        // Keyed by group id: a renamed group must show its new name.
        val setupsByGroupId = mutableMapOf<String, List<ScriptPackageInstance>>()

        fun report() {
            if (groups.all { it.id in setupsByGroupId }) onGroupsChanged(groups.map { it to setupsByGroupId.getValue(it.id) })
        }

        report()
        getScriptsInGroupInteractorJob =
            scope.launch(dispatcherProvider.io) {
                groups.forEach { group ->
                    // Child of the outer job, so cancelling it on the next emission stops these collectors.
                    launch {
                        getScriptsInGroupInteractor(GetScriptsInGroupInteractor.Params(group.id)).collectLatest { result ->
                            withContext(dispatcherProvider.main) {
                                result.handleFailureOrElse(errorResolver) { setups ->
                                    setupsByGroupId[group.id] = setups.map { it.scriptPackageInstance }
                                    report()
                                }
                            }
                        }
                    }
                }
            }
    }

    fun observeTasks(
        scope: CoroutineScope,
        onTasksUpdated: (List<Task>?) -> Unit,
    ) {
        scope.launch(dispatcherProvider.io) {
            observeTasksInteractor(Interactor.None()).collectLatest { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) { tasks -> onTasksUpdated(tasks) }
                }
            }
        }
    }
}

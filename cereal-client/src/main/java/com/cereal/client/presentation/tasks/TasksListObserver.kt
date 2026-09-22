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
import com.cereal.client.presentation.tasks.mappers.GroupListItemContentMapper
import com.cereal.client.presentation.tasks.mappers.toTaskGroupUiModels
import com.cereal.client.presentation.view.group.GroupListItemContent
import com.cereal.client.presentation.view.group.HierarchicalListViewModel
import com.github.kittinunf.result.coroutines.map
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
    val childrenByGroupState =
        mutableMapOf<ScriptPackageGroup, List<GroupListItemContent<ScriptPackageInstance>>>()

    fun observeTaskGroups(
        scope: CoroutineScope,
        hierarchicalListViewModel: HierarchicalListViewModel<ScriptPackageGroup, ScriptPackageInstance>,
        onGroupsStateChanged: (TaskViewState.GroupViewState) -> Unit,
    ) {
        scope.launch(dispatcherProvider.io) {
            getTaskGroupsInteractor(Interactor.None()).collectLatest { result ->
                val mappedResult =
                    result.map { groups ->
                        groups.toTaskGroupUiModels()
                    }

                withContext(dispatcherProvider.main) {
                    mappedResult.handleFailureOrElse(errorResolver) { taskGroupsUi ->
                        // Update hierarchical list with groups (parents will be updated with children later)
                        updateHierarchicalList(scope, taskGroupsUi, hierarchicalListViewModel)

                        if (taskGroupsUi.isEmpty()) {
                            onGroupsStateChanged(TaskViewState.GroupViewState.Empty)
                        } else {
                            onGroupsStateChanged(TaskViewState.GroupViewState.Filled(hierarchicalListViewModel))
                        }
                    }
                }
            }
        }
    }

    private fun updateHierarchicalList(
        scope: CoroutineScope,
        groups: List<GroupListItemContent<ScriptPackageGroup>>,
        hierarchicalListViewModel: HierarchicalListViewModel<ScriptPackageGroup, ScriptPackageInstance>,
    ) {
        // Cancel previous jobs
        getScriptsInGroupInteractorJob?.cancel()

        // Clear children for groups that no longer exist
        childrenByGroupState.keys.retainAll(groups.map { it.id }.toSet())

        // Update hierarchical list with groups (parents will be updated with children later)
        hierarchicalListViewModel.updateItems(
            groups,
            childrenByGroupState.toMap(),
            false,
        )

        if (groups.isEmpty()) {
            return
        }

        // Start observing all groups' children
        getScriptsInGroupInteractorJob =
            scope.launch(dispatcherProvider.io) {
                // Start a separate job for each group to observe its children
                groups.forEach { groupContent ->
                    scope.launch(dispatcherProvider.io) {
                        getScriptsInGroupInteractor(GetScriptsInGroupInteractor.Params(groupContent.id.id))
                            .collectLatest { result ->
                                withContext(dispatcherProvider.main) {
                                    result.handleFailureOrElse(errorResolver) { scriptInstances ->
                                        val scriptInstancesUi =
                                            scriptInstances.map { scriptInstanceInGroup ->
                                                GroupListItemContentMapper.fromScriptInstanceInGroup(
                                                    scriptInstanceInGroup,
                                                )
                                            }

                                        // Update the children state for this group
                                        childrenByGroupState[groupContent.id] = scriptInstancesUi

                                        // Update hierarchical list with current state
                                        hierarchicalListViewModel.updateItems(
                                            groups,
                                            childrenByGroupState.toMap(),
                                            true,
                                        )
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
                    val tasks = result.get()
                    onTasksUpdated(tasks)
                }
            }
        }
    }
}

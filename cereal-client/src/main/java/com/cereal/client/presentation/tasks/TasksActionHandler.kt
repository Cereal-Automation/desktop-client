package com.cereal.client.presentation.tasks

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.task.ChangeScriptPackageInstanceGroupInteractor
import com.cereal.client.application.interactor.task.CreateScriptInstanceGroupInteractor
import com.cereal.client.application.interactor.task.DeleteScriptInstanceInteractor
import com.cereal.client.application.interactor.task.DeleteTaskGroupInteractor
import com.cereal.client.application.interactor.task.EditScriptInstanceGroupInteractor
import com.cereal.client.application.interactor.task.StartAllTasksInScriptPackageInstanceInteractor
import com.cereal.client.application.interactor.task.StartTaskInteractor
import com.cereal.client.application.interactor.task.StopTaskInteractor
import com.cereal.client.application.interactor.task.StopTasksInScriptPackageInstanceInteractor
import com.cereal.client.application.interactor.task.UserInteractionDismissedInteractor
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.error.handleFailureOrElse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Constructor parameters are injected dependencies (interactors and providers).
@Suppress("LongParameterList")
class TasksActionHandler(
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val errorResolver: ErrorResolver,
    private val createScriptInstanceGroupInteractor: CreateScriptInstanceGroupInteractor,
    private val editScriptInstanceGroupInteractor: EditScriptInstanceGroupInteractor,
    private val deleteTaskGroupInteractor: DeleteTaskGroupInteractor,
    private val deleteScriptInstanceInteractor: DeleteScriptInstanceInteractor,
    private val changeScriptPackageInstanceGroupInteractor: ChangeScriptPackageInstanceGroupInteractor,
    private val startTaskInteractor: StartTaskInteractor,
    private val stopTaskInteractor: StopTaskInteractor,
    private val stopTasksInScriptPackageInstanceInteractor: StopTasksInScriptPackageInstanceInteractor,
    private val startAllTasksInScriptPackageInstanceInteractor: StartAllTasksInScriptPackageInstanceInteractor,
    private val userInteractionDismissedInteractor: UserInteractionDismissedInteractor,
) {
    fun createGroup(
        scope: CoroutineScope,
        groupName: String,
        onSuccess: (ScriptPackageGroup) -> Unit,
    ) {
        scope.launch(dispatcherProvider.io) {
            createScriptInstanceGroupInteractor(CreateScriptInstanceGroupInteractor.Params(groupName)) { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) { createdGroup ->
                        onSuccess(createdGroup)
                    }
                }
            }
        }
    }

    fun editGroup(
        scope: CoroutineScope,
        taskGroupId: String,
        groupName: String,
        onSuccess: () -> Unit,
    ) {
        scope.launch(dispatcherProvider.io) {
            editScriptInstanceGroupInteractor(
                EditScriptInstanceGroupInteractor.Params(
                    taskGroupId,
                    groupName,
                ),
            ) { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) {
                        onSuccess()
                    }
                }
            }
        }
    }

    fun deleteGroupInternal(
        scope: CoroutineScope,
        group: ScriptPackageGroup,
        onSuccess: () -> Unit,
    ) {
        scope.launch(dispatcherProvider.io) {
            deleteTaskGroupInteractor(DeleteTaskGroupInteractor.Params(group)) { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) {
                        onSuccess()
                    }
                }
            }
        }
    }

    fun changeGroup(
        scope: CoroutineScope,
        scriptPackageInstance: ScriptPackageInstance,
        newGroup: ScriptPackageGroup,
        onSuccess: () -> Unit,
    ) {
        scope.launch(dispatcherProvider.io) {
            changeScriptPackageInstanceGroupInteractor(
                ChangeScriptPackageInstanceGroupInteractor.Params(
                    scriptPackageInstance = scriptPackageInstance,
                    newGroupId = newGroup.id,
                ),
            ) { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) {
                        onSuccess()
                    }
                }
            }
        }
    }

    fun deleteScriptInstance(
        scope: CoroutineScope,
        scriptPackageInstance: ScriptPackageInstance,
        onSuccess: () -> Unit,
    ) {
        scope.launch(dispatcherProvider.io) {
            deleteScriptInstanceInteractor(DeleteScriptInstanceInteractor.Params(scriptPackageInstance)) { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) {
                        onSuccess()
                    }
                }
            }
        }
    }

    fun startAllTasks(
        scope: CoroutineScope,
        scriptPackageInstance: ScriptPackageInstance,
    ) {
        scope.launch(dispatcherProvider.io) {
            startAllTasksInScriptPackageInstanceInteractor(StartAllTasksInScriptPackageInstanceInteractor.Params(scriptPackageInstance)) { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) {
                        // No-op, tasks states is updated through observer.
                    }
                }
            }
        }
    }

    fun stopAllTasks(
        scope: CoroutineScope,
        scriptPackageInstance: ScriptPackageInstance,
    ) {
        scope.launch(dispatcherProvider.io) {
            stopTasksInScriptPackageInstanceInteractor(StopTasksInScriptPackageInstanceInteractor.Params(scriptPackageInstance)) { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) {
                        // No-op, tasks states is updated through observer.
                    }
                }
            }
        }
    }

    fun startTaskInternal(
        scope: CoroutineScope,
        taskId: String,
    ) {
        scope.launch(dispatcherProvider.io) {
            startTaskInteractor(
                StartTaskInteractor.Params(
                    taskId = taskId,
                ),
            ) { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) {
                        // No-op, state is updated through observer.
                    }
                }
            }
        }
    }

    fun stopTask(
        scope: CoroutineScope,
        taskId: String,
    ) {
        scope.launch(dispatcherProvider.io) {
            stopTaskInteractor(
                StopTaskInteractor.Params(
                    taskId = taskId,
                ),
            ) { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver) {
                        // No-op, state is updated through observer.
                    }
                }
            }
        }
    }

    fun finishUserInteraction(
        scope: CoroutineScope,
        taskId: String,
    ) {
        scope.launch(dispatcherProvider.io) {
            userInteractionDismissedInteractor.run(UserInteractionDismissedInteractor.Params(taskId))
        }
    }
}

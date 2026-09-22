package com.cereal.client.presentation.tasks

import androidx.compose.runtime.mutableStateOf
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.presentation.tasks.model.TaskUiModel

class TaskDialogManager {
    val dialogState = mutableStateOf<TaskViewState.DialogState>(TaskViewState.DialogState.Hidden)

    fun showCreateGroup() {
        dialogState.value = TaskViewState.DialogState.AddingTaskGroup
    }

    fun showEditGroup(group: ScriptPackageGroup) {
        dialogState.value = TaskViewState.DialogState.EditingTaskGroup(group.name)
    }

    fun showConfirmDeletion(group: ScriptPackageGroup) {
        dialogState.value = TaskViewState.DialogState.ConfirmGroupDeletion(group)
    }

    fun showSelectingScript(group: ScriptPackageGroup) {
        dialogState.value = TaskViewState.DialogState.SelectingScript(group)
    }

    fun showConfiguration(scriptPackageInstance: ScriptPackageInstance) {
        dialogState.value = TaskViewState.DialogState.ViewScriptConfiguration(scriptPackageInstance)
    }

    fun showMoveScriptToGroup(
        scriptInstance: ScriptPackageInstance,
        currentGroup: ScriptPackageGroup,
        availableGroups: List<ScriptPackageGroup>,
    ) {
        dialogState.value =
            TaskViewState.DialogState.MovingScript(
                scriptPackageInstance = scriptInstance,
                currentGroup = currentGroup,
                availableGroups = availableGroups,
            )
    }

    fun showDuplicateScript(
        group: ScriptPackageGroup,
        scriptPackageInstance: ScriptPackageInstance,
    ) {
        dialogState.value = TaskViewState.DialogState.DuplicatingScript(group, scriptPackageInstance)
    }

    fun showConfirmTaskRestart(taskUiModel: TaskUiModel) {
        dialogState.value = TaskViewState.DialogState.ConfirmTaskRestart(taskUiModel)
    }

    fun showConfirmScriptInstanceDeletion(scriptPackageInstance: ScriptPackageInstance) {
        dialogState.value = TaskViewState.DialogState.ConfirmScriptInstanceDeletion(scriptPackageInstance)
    }

    fun closeDialog() {
        dialogState.value = TaskViewState.DialogState.Hidden
    }
}

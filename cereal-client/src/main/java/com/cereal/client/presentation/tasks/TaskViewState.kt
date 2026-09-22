package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.presentation.tasks.model.ScriptInstanceUiModel
import com.cereal.client.presentation.tasks.model.TaskUiModel
import com.cereal.client.presentation.view.group.HierarchicalListViewModel

sealed class TaskViewState {
    sealed class GroupViewState {
        data object Empty : GroupViewState()

        data object Loading : GroupViewState()

        class Filled<P, C>(
            val hierarchicalViewModel: HierarchicalListViewModel<P, C>,
        ) : GroupViewState()
    }

    sealed class DetailViewState(
        val headerTitle: String = "Tasks",
        val headerSubtitle: String? = null,
        val editGroupEnabled: Boolean = true,
        val createTaskEnabled: Boolean = true,
        val stopAllTasksEnabled: Boolean = true,
        val startAllTasksEnabled: Boolean = true,
    ) {
        data object NoSelection : DetailViewState(
            editGroupEnabled = false,
            createTaskEnabled = false,
            stopAllTasksEnabled = false,
            startAllTasksEnabled = false,
        )

        class Loading(
            headerTitle: String,
        ) : DetailViewState(
                headerTitle = headerTitle,
                stopAllTasksEnabled = false,
                startAllTasksEnabled = false,
            )

        class Filled(
            val scriptInstances: List<ScriptInstanceUiModel>,
            stopAllTasksEnabled: Boolean,
            startAllTasksEnabled: Boolean,
            headerTitle: String,
            headerSubtitle: String? = null,
            val finishedTasksCount: Int = 0,
            val erroredTasksCount: Int = 0,
            val idleTasksCount: Int = 0,
            val runningTasksCount: Int = 0,
        ) : DetailViewState(
                headerTitle = headerTitle,
                headerSubtitle = headerSubtitle,
                stopAllTasksEnabled = stopAllTasksEnabled,
                startAllTasksEnabled = startAllTasksEnabled,
            )

        class Empty(
            headerTitle: String = "Tasks",
            headerSubtitle: String? = null,
        ) : DetailViewState(
                headerTitle = headerTitle,
                headerSubtitle = headerSubtitle,
                stopAllTasksEnabled = false,
                startAllTasksEnabled = false,
            )
    }

    sealed class DialogState {
        data object Hidden : DialogState()

        data object AddingTaskGroup : DialogState()

        class EditingTaskGroup(
            val initialValue: String,
        ) : DialogState()

        class SelectingScript(
            val scriptPackageGroup: ScriptPackageGroup,
            val initialPublicIdentifier: String? = null,
        ) : DialogState()

        class ViewScriptConfiguration(
            val scriptPackageInstance: ScriptPackageInstance,
        ) : DialogState()

        class DuplicatingScript(
            val scriptPackageGroup: ScriptPackageGroup,
            val scriptPackageInstance: ScriptPackageInstance,
        ) : DialogState()

        class MovingScript(
            val scriptPackageInstance: ScriptPackageInstance,
            val currentGroup: ScriptPackageGroup,
            val availableGroups: List<ScriptPackageGroup>,
        ) : DialogState()

        class ConfirmTaskRestart(
            val taskUiModel: TaskUiModel,
        ) : DialogState()

        class ConfirmGroupDeletion(
            val group: ScriptPackageGroup,
        ) : DialogState()

        class ConfirmScriptInstanceDeletion(
            val scriptPackageInstance: ScriptPackageInstance,
        ) : DialogState()
    }
}

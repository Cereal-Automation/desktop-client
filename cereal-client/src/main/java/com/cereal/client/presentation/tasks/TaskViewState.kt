package com.cereal.client.presentation.tasks

import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.TaskSetupSort
import com.cereal.client.presentation.tasks.model.TaskUiModel

sealed class TaskViewState {
    /** The task list: every task setup, grouped by group. */
    sealed class ListState {
        /** Before the groups and their setups first load; renders nothing so the empty state never flashes. */
        data object Loading : ListState()

        /** No task setups (and no groups beyond the default one). */
        data object Empty : ListState()

        data class Filled(
            /** The groups and setups the filters leave. */
            val groups: List<TaskGroupSection>,
            /** Every setup and task, whatever the filters leave. */
            val setupCount: Int,
            val taskCount: Int,
            /** The status chips to show, in order. */
            val chips: List<TaskFilterChip>,
            /** Offered only with more than one group. */
            val groupFilter: GroupFilterState?,
            /** How each group's setups are ordered. */
            val sort: TaskSetupSort,
        ) : ListState()
    }

    sealed class DialogState {
        data object Hidden : DialogState()

        data object AddingTaskGroup : DialogState()

        class EditingTaskGroup(
            val group: ScriptPackageGroup,
        ) : DialogState() {
            val initialValue: String get() = group.name
        }

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

        class EditingScript(
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

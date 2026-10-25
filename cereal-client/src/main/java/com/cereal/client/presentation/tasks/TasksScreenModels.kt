package com.cereal.client.presentation.tasks

import androidx.compose.ui.unit.dp
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.presentation.tasks.model.ScriptInstanceUiModel
import com.cereal.client.presentation.tasks.model.TaskUiModel
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.contact_support
import com.cereal_automation.cereal_client.generated.resources.delete
import com.cereal_automation.cereal_client.generated.resources.duplicate
import com.cereal_automation.cereal_client.generated.resources.edit
import com.cereal_automation.cereal_client.generated.resources.filter_all
import com.cereal_automation.cereal_client.generated.resources.filter_failed
import com.cereal_automation.cereal_client.generated.resources.idle
import com.cereal_automation.cereal_client.generated.resources.move_to_group
import com.cereal_automation.cereal_client.generated.resources.rename
import com.cereal_automation.cereal_client.generated.resources.restart_failed_count
import com.cereal_automation.cereal_client.generated.resources.running
import com.cereal_automation.cereal_client.generated.resources.success
import com.cereal_automation.cereal_client.generated.resources.view_full_configuration
import org.jetbrains.compose.resources.StringResource

internal val PanePadding = 20.dp

/** The status chips, in chip order. */
enum class TaskFilter { ALL, RUNNING, ERROR, SUCCESS, IDLE }

internal fun TaskFilter.matches(status: TaskStatus): Boolean =
    when (this) {
        TaskFilter.ALL -> true
        TaskFilter.RUNNING -> status is TaskStatus.Running
        TaskFilter.ERROR -> status is TaskStatus.Error
        TaskFilter.SUCCESS -> status is TaskStatus.Success
        TaskFilter.IDLE -> status is TaskStatus.Idle
    }

internal fun TaskFilter.labelRes() =
    when (this) {
        TaskFilter.ALL -> Res.string.filter_all
        TaskFilter.RUNNING -> Res.string.running
        TaskFilter.IDLE -> Res.string.idle
        TaskFilter.SUCCESS -> Res.string.success
        TaskFilter.ERROR -> Res.string.filter_failed
    }

/** A status chip: its tasks across every listed setup, whatever chip is active. */
data class TaskFilterChip(
    val filter: TaskFilter,
    val count: Int,
    val active: Boolean,
)

/** The group filter: "All groups" ([selectedGroupId] null) or one of [groups]. */
data class GroupFilterState(
    val groups: List<GroupFilterOption>,
    val selectedGroupId: String?,
)

data class GroupFilterOption(
    val id: String,
    val name: String,
)

/** A group's label in the task list and the task setups beneath it. */
data class TaskGroupSection(
    val groupId: String,
    val name: String,
    val setups: List<TaskSetupSection>,
)

/** One task setup in the task list: its header row and its task rows. */
data class TaskSetupSection(
    val setupId: String,
    val name: String,
    val subtitle: String?,
    val runningCount: Int,
    val failedCount: Int,
    val taskCount: Int,
    /** Null when the setup always runs one task. */
    val stepper: ConcurrentTaskStepperState?,
    /** The Start all / Stop all toggle offers Stop all while any task runs. */
    val showStopAll: Boolean,
    /** Start all has something to start. */
    val canStartAll: Boolean,
    val menuOptions: List<SetupMenuOption>,
    /** Edit is offered only while none of its tasks runs. */
    val editable: Boolean,
    /** False while the user has collapsed it: its task rows are not shown. */
    val expanded: Boolean,
    /** Task rows per script: the main script first, then child scripts (which carry a title). */
    val scripts: List<ScriptInstanceUiModel>,
)

/** A task setup row's "···" menu entries. */
enum class SetupMenuOption(
    val label: StringResource,
) {
    EDIT(Res.string.edit),
    VIEW_CONFIGURATION(Res.string.view_full_configuration),
    DUPLICATE(Res.string.duplicate),
    MOVE_TO_GROUP(Res.string.move_to_group),

    /** Takes the number of failed tasks as its format argument. */
    RESTART_FAILED(Res.string.restart_failed_count),
    CONTACT_SUPPORT(Res.string.contact_support),
    DELETE(Res.string.delete),
}

/** A group label's "···" menu entries. */
enum class GroupMenuOption(
    val label: StringResource,
) {
    RENAME(Res.string.rename),
    DELETE(Res.string.delete),
}

/** A task setup's `− count +` stepper for its number of concurrent tasks. */
data class ConcurrentTaskStepperState(
    val count: Int,
    val canDecrease: Boolean,
    val canIncrease: Boolean,
)

/** The task inspector: the selected task, live, and the actions its status allows. */
data class TaskInspectorState(
    val task: TaskUiModel,
    val actions: List<TaskInspectorAction>,
)

enum class TaskInspectorAction { STOP, START, RESTART, REPORT_ISSUE }

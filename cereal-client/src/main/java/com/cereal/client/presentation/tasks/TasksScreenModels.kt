package com.cereal.client.presentation.tasks

import androidx.compose.ui.unit.dp
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.presentation.tasks.model.ScriptInstanceUiModel
import com.cereal.client.presentation.tasks.model.TaskUiModel
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.errored
import com.cereal_automation.cereal_client.generated.resources.filter_all
import com.cereal_automation.cereal_client.generated.resources.idle
import com.cereal_automation.cereal_client.generated.resources.running
import com.cereal_automation.cereal_client.generated.resources.success

internal val PanePadding = 20.dp

internal enum class TaskFilter { ALL, RUNNING, IDLE, SUCCESS, ERROR }

internal fun TaskFilter.matches(task: TaskUiModel): Boolean =
    when (this) {
        TaskFilter.ALL -> true
        TaskFilter.RUNNING -> task.isRunning
        TaskFilter.ERROR -> task.isError
        TaskFilter.SUCCESS -> task.isSuccess
        TaskFilter.IDLE -> task.isIdle
    }

internal fun TaskFilter.labelRes() =
    when (this) {
        TaskFilter.ALL -> Res.string.filter_all
        TaskFilter.RUNNING -> Res.string.running
        TaskFilter.IDLE -> Res.string.idle
        TaskFilter.SUCCESS -> Res.string.success
        TaskFilter.ERROR -> Res.string.errored
    }

/** A group's label in the task list and the task setups beneath it. */
data class TaskGroupSection(
    val group: ScriptPackageGroup,
    /** [group]'s name, held apart because a group equals any version of itself by id: a rename must still change the section. */
    val name: String,
    val setups: List<TaskSetupSection>,
)

/** One task setup in the task list: its header row and its task rows. */
data class TaskSetupSection(
    val setup: ScriptPackageInstance,
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
    val menuOptions: List<MenuOption>,
    /** Task rows per script: the main script first, then child scripts (which carry a title). */
    val scripts: List<ScriptInstanceUiModel>,
)

/** A task setup's `− count +` stepper for its number of concurrent tasks. */
data class ConcurrentTaskStepperState(
    val count: Int,
    val canDecrease: Boolean,
    val canIncrease: Boolean,
)

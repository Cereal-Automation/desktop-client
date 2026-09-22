package com.cereal.client.presentation.tasks

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.tasks.model.TaskUiModel
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.errored
import com.cereal_automation.cereal_client.generated.resources.filter_all
import com.cereal_automation.cereal_client.generated.resources.idle
import com.cereal_automation.cereal_client.generated.resources.running
import com.cereal_automation.cereal_client.generated.resources.success

internal val PanePadding = 20.dp

internal enum class TaskFilter { ALL, RUNNING, IDLE, SUCCESS, ERROR }

@Immutable
internal data class TaskCounts(
    val idle: Int,
    val running: Int,
    val finished: Int,
    val errored: Int,
) {
    val total: Int get() = idle + running + finished + errored
}

@Immutable
internal data class TaskQuickActionsState(
    val startAllEnabled: Boolean,
    val stopAllEnabled: Boolean,
    val restartErroredEnabled: Boolean,
    val filter: TaskFilter,
)

@Stable
internal class TaskQuickActionsCallbacks(
    val onFilterChange: (TaskFilter) -> Unit,
    val onStartAll: () -> Unit,
    val onStopAll: () -> Unit,
    val onRestartErrored: () -> Unit,
)

internal fun TaskFilter.matches(task: TaskUiModel): Boolean =
    when (this) {
        TaskFilter.ALL -> true
        TaskFilter.RUNNING -> task.isRunning
        TaskFilter.ERROR -> task.isError
        TaskFilter.SUCCESS -> task.isSuccess
        TaskFilter.IDLE -> !task.isRunning && !task.isError && !task.isSuccess
    }

internal fun TaskFilter.labelRes() =
    when (this) {
        TaskFilter.ALL -> Res.string.filter_all
        TaskFilter.RUNNING -> Res.string.running
        TaskFilter.IDLE -> Res.string.idle
        TaskFilter.SUCCESS -> Res.string.success
        TaskFilter.ERROR -> Res.string.errored
    }

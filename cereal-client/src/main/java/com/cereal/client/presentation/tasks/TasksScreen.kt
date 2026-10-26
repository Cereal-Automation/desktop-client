package com.cereal.client.presentation.tasks

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.presentation.error.errorView
import com.cereal.client.presentation.group.GroupNameDialog
import com.cereal.client.presentation.tasks.dialog.MoveScriptDialog
import com.cereal.client.presentation.tasks.dialog.ViewConfigurationDialog
import com.cereal.client.presentation.tasks.model.ScriptInstanceUiModel
import com.cereal.client.presentation.tasks.model.TaskUiModel
import com.cereal.client.presentation.tasks.script.overview.ScriptRequest
import com.cereal.client.presentation.tasks.script.overview.ScriptSelectionDialog
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.ConfirmationDialog
import com.cereal.client.presentation.view.VerticalDivider
import com.cereal.client.presentation.view.rememberFileDialogLauncher
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.cancel
import com.cereal_automation.cereal_client.generated.resources.delete
import com.cereal_automation.cereal_client.generated.resources.delete_group_confirmation_message
import com.cereal_automation.cereal_client.generated.resources.delete_group_confirmation_title
import com.cereal_automation.cereal_client.generated.resources.delete_script_instance_confirmation_message
import com.cereal_automation.cereal_client.generated.resources.delete_script_instance_confirmation_title
import com.cereal_automation.cereal_client.generated.resources.restart
import com.cereal_automation.cereal_client.generated.resources.restart_task
import com.cereal_automation.cereal_client.generated.resources.restart_task_message
import org.jetbrains.compose.resources.stringResource
import org.koin.java.KoinJavaComponent
import java.awt.FileDialog
import kotlin.coroutines.resume

@Composable
fun TasksScreen(
    onNavigateToMarketplace: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    openScriptSelectionFor: ScriptRequest? = null,
    focusTaskId: String? = null,
    viewModel: TasksViewModel =
        remember {
            KoinJavaComponent.get(
                TasksViewModel::class.java,
            )
        },
) {
    DisposableEffect(viewModel) {
        onDispose { viewModel.dispose() }
    }

    LaunchedEffect(openScriptSelectionFor) {
        openScriptSelectionFor?.let {
            viewModel.openScriptSelection(it)
        }
    }

    LaunchedEffect(focusTaskId) {
        focusTaskId?.let { viewModel.focusTask(it) }
    }

    val dialogState by viewModel.dialogState
    val listState by viewModel.listState

    Column(Modifier.fillMaxSize()) {
        TasksHeader(
            counts =
                when (val state = listState) {
                    is TaskViewState.ListState.Filled -> state.setupCount to state.taskCount
                    TaskViewState.ListState.Empty -> 0 to 0
                    TaskViewState.ListState.Loading -> null
                },
            onNewGroup = viewModel::onCreateTaskGroup,
            onNewTaskSetup = viewModel::onAddScriptClicked,
        )
        // The inspector sits beside the filter bar and list, under the header.
        // With the inspector open it takes a third of the width (never narrower than its content needs).
        BoxWithConstraints(Modifier.weight(1.0f).fillMaxWidth()) {
            val inspectorWidth = maxOf(maxWidth / 3, InspectorMinWidth)
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1.0f).fillMaxHeight()) {
                    (listState as? TaskViewState.ListState.Filled)?.let { state ->
                        TaskFilterBar(state.chips, state.groupFilter, viewModel::onStatusFilterSelected, viewModel::onGroupFilterSelected)
                    }
                    Box(Modifier.weight(1.0f).fillMaxWidth()) {
                        when (val state = listState) {
                            is TaskViewState.ListState.Filled -> {
                                TaskListContent(state, viewModel)
                            }

                            TaskViewState.ListState.Empty -> {
                                TasksEmptyState(
                                    onNewTaskSetup = viewModel::onAddScriptClicked,
                                    onNavigateToHome = onNavigateToHome,
                                )
                            }

                            // Loading is the brief window before the groups first load. Render nothing here so the
                            // empty-state CTA never flashes ahead of the real list.
                            TaskViewState.ListState.Loading -> {}
                        }
                    }
                }

                viewModel.inspector.value?.let { inspector ->
                    VerticalDivider()
                    val openFileDialog = rememberFileDialogLauncher()
                    TaskInspector(
                        state = inspector,
                        events = viewModel.logViewerMessages.value,
                        artifacts = viewModel.artifacts.value,
                        onClose = viewModel::closeInspector,
                        onAction = viewModel::onInspectorAction,
                        onClearLog = viewModel::clearLogs,
                        onDownloadArtifact = { artifact ->
                            openFileDialog(FileDialog.SAVE)?.let { file ->
                                viewModel.onDownloadArtifact(artifact, file)
                            }
                        },
                        modifier = Modifier.width(inspectorWidth),
                    )
                }
            }
        }
    }

    when (val state = dialogState) {
        TaskViewState.DialogState.Hidden -> {}

        is TaskViewState.DialogState.AddingTaskGroup -> {
            GroupNameDialog(
                onDismissRequest = {
                    viewModel.closeDialog()
                },
                onConfirmClicked = { value ->
                    viewModel.createGroup(value)
                },
            )
        }

        is TaskViewState.DialogState.EditingTaskGroup -> {
            GroupNameDialog(
                initialValue = state.initialValue,
                onDismissRequest = {
                    viewModel.closeDialog()
                },
                onDeleteClicked = {
                    viewModel.deleteGroup(state.group)
                },
                onConfirmClicked = { value ->
                    viewModel.editGroup(state.group, groupName = value)
                },
            )
        }

        is TaskViewState.DialogState.SelectingScript -> {
            ScriptSelectionDialog(
                scriptPackageGroup = state.scriptPackageGroup,
                initialRequest = state.initialRequest,
                onDismissRequest = { viewModel.closeDialog() },
                // The new setup shows up in the list through its flows.
                onScriptInstanceCreated = {},
                onNavigateToMarketplace = {
                    viewModel.closeDialog()
                    onNavigateToMarketplace()
                },
            )
        }

        is TaskViewState.DialogState.EditingScript -> {
            ScriptSelectionDialog(
                scriptPackageGroup = state.scriptPackageGroup,
                initialScriptPackageInstance = state.scriptPackageInstance,
                editing = true,
                onDismissRequest = { viewModel.closeDialog() },
                onScriptInstanceCreated = {},
            )
        }

        is TaskViewState.DialogState.MovingScript -> {
            MoveScriptDialog(
                currentGroup = state.currentGroup,
                availableGroups = state.availableGroups,
                onDismissRequest = { viewModel.closeDialog() },
                onMoveClicked = { newGroup ->
                    viewModel.changeGroup(state.scriptPackageInstance, newGroup)
                },
            )
        }

        is TaskViewState.DialogState.ViewScriptConfiguration -> {
            ViewConfigurationDialog(
                scriptPackageInstance = state.scriptPackageInstance,
                onDismissRequest = { viewModel.closeDialog() },
            )
        }

        is TaskViewState.DialogState.DuplicatingScript -> {
            ScriptSelectionDialog(
                scriptPackageGroup = state.scriptPackageGroup,
                initialScriptPackageInstance = state.scriptPackageInstance,
                onDismissRequest = { viewModel.closeDialog() },
                // The new setup shows up in the list through its flows.
                onScriptInstanceCreated = {},
                onNavigateToMarketplace = {
                    viewModel.closeDialog()
                    onNavigateToMarketplace()
                },
            )
        }

        is TaskViewState.DialogState.ConfirmTaskRestart -> {
            ConfirmationDialog(
                title = stringResource(Res.string.restart_task),
                message = stringResource(Res.string.restart_task_message),
                confirmButtonText = stringResource(Res.string.restart),
                dismissButtonText = stringResource(Res.string.cancel),
                onConfirm = { viewModel.confirmTaskRestart(state.taskUiModel) },
                onDismiss = { viewModel.closeDialog() },
            )
        }

        is TaskViewState.DialogState.ConfirmGroupDeletion -> {
            ConfirmationDialog(
                title = stringResource(Res.string.delete_group_confirmation_title),
                message = stringResource(Res.string.delete_group_confirmation_message),
                confirmButtonText = stringResource(Res.string.delete),
                dismissButtonText = stringResource(Res.string.cancel),
                onConfirm = { viewModel.confirmGroupDeletion(state.group) },
                onDismiss = { viewModel.closeDialog() },
            )
        }

        is TaskViewState.DialogState.ConfirmScriptInstanceDeletion -> {
            ConfirmationDialog(
                title = stringResource(Res.string.delete_script_instance_confirmation_title),
                message = stringResource(Res.string.delete_script_instance_confirmation_message, state.scriptPackageInstance.entryName),
                confirmButtonText = stringResource(Res.string.delete),
                dismissButtonText = stringResource(Res.string.cancel),
                onConfirm = { viewModel.confirmScriptInstanceDeletion(state.scriptPackageInstance) },
                onDismiss = { viewModel.closeDialog() },
            )
        }
    }

    errorView(viewModel.errorAction)

    for (taskUiModel in viewModel.userInteractions.value) {
        taskUiModel.id.userInteraction?.let { userInteraction ->
            key(taskUiModel.id.id) {
                UserInteractionWindow(
                    userInteraction = userInteraction,
                    taskNumber = taskUiModel.taskNumber,
                    closeWindow = {
                        viewModel.finishUserInteraction(taskUiModel)
                    },
                )
            }
        }
    }
}

/** One list item: lets [TaskListContent] find a task's index to scroll to. */
private sealed interface TaskListItem {
    val key: String

    data class GroupLabel(
        val section: TaskGroupSection,
    ) : TaskListItem {
        override val key get() = "group-${section.groupId}"
    }

    data class SetupHeader(
        val section: TaskSetupSection,
        val last: Boolean,
    ) : TaskListItem {
        override val key get() = "setup-${section.setupId}"
    }

    data class ScriptTitle(
        val script: ScriptInstanceUiModel,
        val title: String,
    ) : TaskListItem {
        override val key get() = "script-${script.id.id}"
    }

    data class Row(
        val task: TaskUiModel,
        val last: Boolean,
    ) : TaskListItem {
        override val key get() = task.id.id
    }
}

/**
 * Group labels, then each setup as a card: its header row, then (unless collapsed) its task rows (child scripts under
 * their title). A setup with a single task is just its header, which shows that task.
 */
private fun TaskViewState.ListState.Filled.items(): List<TaskListItem> =
    groups.flatMap { group ->
        listOf(TaskListItem.GroupLabel(group)) +
            group.setups.flatMap { setup ->
                val rows =
                    setup.scripts.takeIf { setup.expanded && setup.singleTask == null }.orEmpty().flatMap { script ->
                        listOfNotNull(script.title?.let { TaskListItem.ScriptTitle(script, it) }) + script.tasks.map { TaskListItem.Row(it, last = false) }
                    }
                val lastRow = rows.lastOrNull() as? TaskListItem.Row
                listOf(TaskListItem.SetupHeader(setup, last = rows.isEmpty())) +
                    rows.map { if (it === lastRow) it.copy(last = true) else it }
            }
    }

@Composable
private fun TaskListContent(
    state: TaskViewState.ListState.Filled,
    viewModel: TasksViewModel,
) {
    val items = remember(state) { state.items() }
    val lazyListState = rememberLazyListState()
    val scrollToTaskId by viewModel.scrollToTaskId
    LaunchedEffect(scrollToTaskId, items) {
        val taskId = scrollToTaskId ?: return@LaunchedEffect
        val index =
            items.indexOfFirst {
                (it is TaskListItem.Row && it.task.id.id == taskId) || (
                    it is TaskListItem.SetupHeader && it.section.singleTask
                        ?.id
                        ?.id == taskId
                )
            }
        if (index >= 0) {
            lazyListState.animateScrollToItem(index)
            viewModel.onScrolledToTask()
        }
    }
    val cardFill = CerealTheme.colorScheme.cardDark
    val cardBorder = CerealTheme.colorScheme.border
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = lazyListState, contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
            items(items, key = { it.key }) { item ->
                when (item) {
                    is TaskListItem.GroupLabel -> {
                        TaskGroupLabel(item.section) { option -> viewModel.onGroupMenuOptionSelected(item.section.groupId, option) }
                    }

                    is TaskListItem.SetupHeader -> {
                        val setup = item.section.setupId
                        val singleTask = item.section.singleTask
                        Box(Modifier.setupCardSlice(first = true, last = item.last, fill = cardFill, border = cardBorder)) {
                            if (singleTask != null) {
                                SingleTaskSetupRow(
                                    section = item.section,
                                    task = singleTask,
                                    isSelected = viewModel.selectedTask.value?.id == singleTask.id.id,
                                    onClick = { viewModel.onTaskClicked(singleTask.id) },
                                    onStartTask = { viewModel.startTask(singleTask) },
                                    onStopTask = { viewModel.stopTask(singleTask) },
                                    onContinueClick = { continueTask(singleTask, viewModel) },
                                    onDecreaseTasks = { viewModel.decreaseConcurrentTasks(setup) },
                                    onIncreaseTasks = { viewModel.increaseConcurrentTasks(setup) },
                                    onMenuOptionSelected = { option -> viewModel.onSetupMenuOptionSelected(setup, option) },
                                )
                            } else {
                                TaskSetupRow(
                                    section = item.section,
                                    onToggleExpanded = { viewModel.onSetupExpandToggled(setup) },
                                    onStartAll = { viewModel.startAllTasks(setup) },
                                    onStopAll = { viewModel.stopAllTasks(setup) },
                                    onDecreaseTasks = { viewModel.decreaseConcurrentTasks(setup) },
                                    onIncreaseTasks = { viewModel.increaseConcurrentTasks(setup) },
                                    onMenuOptionSelected = { option -> viewModel.onSetupMenuOptionSelected(setup, option) },
                                )
                            }
                        }
                    }

                    is TaskListItem.ScriptTitle -> {
                        Box(Modifier.setupCardSlice(first = false, last = false, fill = cardFill, border = cardBorder)) {
                            ChildScriptTitle(item.title)
                        }
                    }

                    is TaskListItem.Row -> {
                        Box(Modifier.setupCardSlice(first = false, last = item.last, fill = cardFill, border = cardBorder)) {
                            TaskListRow(item.task, viewModel)
                        }
                    }
                }
            }
        }

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = lazyListState),
        )
    }
}

@Composable
private fun TaskListRow(
    task: TaskUiModel,
    viewModel: TasksViewModel,
) {
    TaskRow(
        taskItem = task,
        isSelected = viewModel.selectedTask.value?.id == task.id.id,
        onStartTask = { viewModel.startTask(task) },
        onStopTask = { viewModel.stopTask(task) },
        onRowClick = { viewModel.onTaskClicked(task.id) },
        onContinueClick = { continueTask(task, viewModel) },
    )
}

private fun continueTask(
    task: TaskUiModel,
    viewModel: TasksViewModel,
) {
    // The button lingers until the cleared interaction propagates; a second
    // click must not resume the continuation again.
    (task.id.userInteraction as? UserInteraction.ContinueButton)
        ?.takeIf { btn -> !btn.continuation.isCompleted }
        ?.let { btn ->
            btn.continuation.resume(Unit)
            viewModel.finishUserInteraction(task)
        }
}

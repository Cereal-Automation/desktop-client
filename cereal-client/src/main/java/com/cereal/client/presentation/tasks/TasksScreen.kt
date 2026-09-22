package com.cereal.client.presentation.tasks

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.presentation.error.errorView
import com.cereal.client.presentation.group.GroupNameDialog
import com.cereal.client.presentation.logs.LogOutputView
import com.cereal.client.presentation.tasks.dialog.MoveScriptDialog
import com.cereal.client.presentation.tasks.dialog.ViewConfigurationDialog
import com.cereal.client.presentation.tasks.script.overview.ScriptSelectionDialog
import com.cereal.client.presentation.view.ConfirmationDialog
import com.cereal.client.presentation.view.VerticalDivider
import com.cereal.client.presentation.view.group.ScriptsColumn
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
    openScriptSelectionForPackage: String? = null,
    focusTaskId: String? = null,
    viewModel: TasksViewModel =
        remember {
            KoinJavaComponent.get(
                TasksViewModel::class.java,
            )
        },
) {
    LaunchedEffect(openScriptSelectionForPackage) {
        openScriptSelectionForPackage?.let {
            viewModel.openScriptSelectionForPublicIdentifier(it)
        }
    }

    LaunchedEffect(focusTaskId) {
        focusTaskId?.let { viewModel.focusTask(it) }
    }

    val dialogState by viewModel.dialogState
    val hierarchicalListState by viewModel.hierarchicalListState
    val detailsViewState by viewModel.tasksViewState

    Column {
        Row(Modifier.fillMaxWidth().weight(1.0f)) {
            Box(Modifier.width(320.dp)) {
                when (hierarchicalListState) {
                    is TaskViewState.GroupViewState.Filled<*, *> -> {
                        ScriptsColumn(
                            viewModel = viewModel.hierarchicalListViewModel,
                            onCreateGroup = { viewModel.onCreateTaskGroup() },
                            onChildLongClick = { child ->
                                child?.let {
                                    viewModel.showConfiguration(it.content.id)
                                }
                            },
                        )
                    }

                    TaskViewState.GroupViewState.Empty -> {
                        TasksEmptyState(
                            onCreateGroup = { viewModel.onCreateTaskGroup() },
                            onAddScript = { viewModel.onAddScriptClicked() },
                        )
                    }

                    // Loading is the brief window before the groups flow first emits. Render
                    // nothing here so the empty-state CTA never flashes ahead of the real list.
                    TaskViewState.GroupViewState.Loading -> {}
                }
            }

            VerticalDivider()

            Box {
                Column(Modifier.fillMaxSize()) {
                    val filled = detailsViewState as? TaskViewState.DetailViewState.Filled
                    val counts =
                        remember(filled) {
                            filled?.let {
                                TaskCounts(
                                    idle = it.idleTasksCount,
                                    running = it.runningTasksCount,
                                    finished = it.finishedTasksCount,
                                    errored = it.erroredTasksCount,
                                )
                            }
                        }

                    val supportUrl =
                        viewModel.hierarchicalListViewModel.selectedChild
                            ?.definition
                            ?.manifest
                            ?.supportUrl

                    TasksToolbar(
                        title = detailsViewState.headerTitle,
                        subtitle = detailsViewState.headerSubtitle,
                        onViewConfig =
                            filled?.let {
                                {
                                    viewModel.hierarchicalListViewModel.selectedChild?.let { instance ->
                                        viewModel.showConfiguration(instance)
                                    }
                                }
                            },
                        onContactSupport =
                            if (filled != null && !supportUrl.isNullOrBlank()) {
                                { viewModel.contactSupport() }
                            } else {
                                null
                            },
                    )

                    var taskFilter by rememberSaveable { mutableStateOf(TaskFilter.ALL) }

                    if (filled != null && counts != null) {
                        TaskStatsStrip(counts = counts)

                        TasksQuickActions(
                            state =
                                TaskQuickActionsState(
                                    startAllEnabled = filled.startAllTasksEnabled,
                                    stopAllEnabled = filled.stopAllTasksEnabled,
                                    restartErroredEnabled =
                                        counts.errored > 0 && !viewModel.restartErroredInFlight.value,
                                    filter = taskFilter,
                                ),
                            callbacks =
                                remember(viewModel) {
                                    TaskQuickActionsCallbacks(
                                        onFilterChange = { taskFilter = it },
                                        onStartAll = { viewModel.startAllTasks() },
                                        onStopAll = { viewModel.stopAllTasks() },
                                        onRestartErrored = { viewModel.restartErroredTasks() },
                                    )
                                },
                        )
                    }

                    val listState = rememberLazyListState()
                    Box(Modifier.weight(1.0f)) {
                        filled?.let { filledState ->
                            // Pre-filter and group outside the LazyColumn content lambda so this
                            // only re-runs when the data or the filter actually changes, not on
                            // every recomposition of the list.
                            val visibleGroups =
                                remember(filledState, taskFilter) {
                                    filledState.scriptInstances.mapNotNull { scriptInstance ->
                                        val visibleTasks =
                                            scriptInstance.tasks.filter { task -> taskFilter.matches(task) }
                                        if (visibleTasks.isEmpty()) null else scriptInstance to visibleTasks
                                    }
                                }
                            LazyColumn(state = listState) {
                                visibleGroups.forEach { (scriptInstance, visibleTasks) ->
                                    scriptInstance.title?.let { title ->
                                        item(key = "group-${scriptInstance.id.id}") {
                                            TaskGroupTitle(title)
                                        }
                                    }

                                    items(visibleTasks, key = { it.id.id }) {
                                        TaskRow(
                                            taskItem = it,
                                            isSelected = viewModel.selectedTask.value?.id == it.id.id,
                                            onStartTask = { viewModel.startTask(it) },
                                            onStopTask = { viewModel.stopTask(it) },
                                            onRowClick = { viewModel.onTaskSelected(it.id) },
                                            onContinueClick = {
                                                (it.id.userInteraction as? UserInteraction.ContinueButton)?.let { btn ->
                                                    btn.continuation.resume(Unit)
                                                    viewModel.finishUserInteraction(it)
                                                }
                                            },
                                            onReportIssue =
                                                if (it.isError && it.hasSupportUrl) {
                                                    { viewModel.reportIssue(it) }
                                                } else {
                                                    null
                                                },
                                        )
                                    }
                                }
                            }

                            VerticalScrollbar(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                adapter = rememberScrollbarAdapter(scrollState = listState),
                            )
                        }
                    }

                    val openFileDialog = rememberFileDialogLauncher()
                    LogOutputView(
                        events = viewModel.logViewerMessages.value,
                        onClear = viewModel::clearLogs,
                        artifacts = viewModel.artifacts.value,
                        onDownloadArtifact = { artifact ->
                            openFileDialog(FileDialog.SAVE)?.let { file ->
                                viewModel.onDownloadArtifact(artifact, file)
                            }
                        },
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
                    viewModel.deleteGroup()
                },
                onConfirmClicked = { value ->
                    viewModel.editGroup(groupName = value)
                },
            )
        }

        is TaskViewState.DialogState.SelectingScript -> {
            ScriptSelectionDialog(
                scriptPackageGroup = state.scriptPackageGroup,
                initialPublicIdentifier = state.initialPublicIdentifier,
                onDismissRequest = { viewModel.closeDialog() },
                onScriptInstanceCreated = { scriptPackageInstance: ScriptPackageInstance ->
                    viewModel.onScriptInstanceCreated(scriptPackageInstance)
                },
                onNavigateToMarketplace = {
                    viewModel.closeDialog()
                    onNavigateToMarketplace()
                },
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
                onScriptInstanceCreated = { scriptPackageInstance: ScriptPackageInstance ->
                    viewModel.onScriptInstanceCreated(scriptPackageInstance)
                },
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
                message = stringResource(Res.string.delete_script_instance_confirmation_message, state.scriptPackageInstance.definition.manifest.name),
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

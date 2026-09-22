package com.cereal.client.presentation.tasks

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.artifact.ObserveArtifactsInteractor
import com.cereal.client.application.interactor.artifact.SaveArtifactToFileInteractor
import com.cereal.client.application.interactor.script.ReportScriptIssueInteractor
import com.cereal.client.application.interactor.settings.OpenUrlInteractor
import com.cereal.client.application.interactor.settings.developers.ObserveShowDebugLogsInteractor
import com.cereal.client.application.interactor.task.GetOrCreateDefaultTaskGroupInteractor
import com.cereal.client.domain.model.artifact.Artifact
import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.logging.LoggingPriority
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.repository.LogEventRepository
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.tasks.model.TaskUiModel
import com.cereal.client.presentation.util.InteractorRunner
import com.cereal.client.presentation.view.group.HierarchicalListViewModel
import com.github.kittinunf.result.coroutines.SuspendableResult
import com.github.kittinunf.result.coroutines.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import kotlin.time.ExperimentalTime

// Coordinates the tasks screen, so it exposes more than the default function threshold and takes
// more than the default number of injected dependencies (interactors and providers).
@Suppress("TooManyFunctions", "LongParameterList")
class TasksViewModel(
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val getOrCreateDefaultTaskGroupInteractor: GetOrCreateDefaultTaskGroupInteractor,
    private val tasksActionHandler: TasksActionHandler,
    private val tasksListObserver: TasksListObserver,
    private val errorResolver: ErrorResolver,
    private val reportScriptIssueInteractor: ReportScriptIssueInteractor,
    private val logEventRepository: LogEventRepository,
    private val observeShowDebugLogsInteractor: ObserveShowDebugLogsInteractor,
    private val openUrlInteractor: OpenUrlInteractor,
    private val observeArtifactsInteractor: ObserveArtifactsInteractor,
    private val saveArtifactToFileInteractor: SaveArtifactToFileInteractor,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)
    private val interactorRunner = InteractorRunner(scope, dispatcherProvider, errorResolver)
    val hierarchicalListViewModel =
        HierarchicalListViewModel<ScriptPackageGroup, ScriptPackageInstance>(
            parentMenuOptions = listOf(MenuOption.EDIT, MenuOption.DELETE),
            childMenuOptions = listOf(MenuOption.VIEW_CONFIGURATION, MenuOption.DUPLICATE, MenuOption.MOVE_TO_GROUP, MenuOption.DELETE),
            onParentSelectedChange = { group ->
                // When a group is selected, show Empty state (no scripts selected yet)
                // This enables the edit and create buttons but shows no tasks
                if (group != null) {
                    tasksViewState.value = TaskViewState.DetailViewState.Empty()
                } else {
                    tasksViewState.value = TaskViewState.DetailViewState.NoSelection
                }
            },
            onChildSelectedChange = { scriptInstance ->
                updateTasks(scriptInstance)
            },
            onParentMenuOptionSelected = { group, menuItem ->
                if (menuItem == MenuOption.DELETE) {
                    onDeleteGroup(group)
                } else if (menuItem == MenuOption.EDIT) {
                    onEditTaskGroup(group)
                }
            },
            onChildMenuOptionSelected = { scriptInstance, menuItem ->
                if (menuItem == MenuOption.DELETE) {
                    onDeleteScriptInstance(scriptInstance)
                } else if (menuItem == MenuOption.VIEW_CONFIGURATION) {
                    showConfiguration(scriptInstance)
                } else if (menuItem == MenuOption.DUPLICATE) {
                    duplicateScriptInstance(scriptInstance)
                } else if (menuItem == MenuOption.MOVE_TO_GROUP) {
                    moveScriptToGroup(scriptInstance)
                }
            },
            onAddChildClicked = { group ->
                // Open the script selection dialog for this group
                dialogManager.showSelectingScript(group)
            },
        )

    // Start in Loading, not Empty: the groups flow has not emitted yet on screen entry, and
    // rendering the Empty "create your first group" CTA in that window flickers before the real
    // list arrives. observeTaskGroups() resolves this to Filled/Empty once the data is in.
    val hierarchicalListState =
        mutableStateOf<TaskViewState.GroupViewState>(TaskViewState.GroupViewState.Loading)
    val tasksViewState = mutableStateOf<TaskViewState.DetailViewState>(TaskViewState.DetailViewState.NoSelection)
    private val dialogManager = TaskDialogManager()
    val dialogState get() = dialogManager.dialogState
    val logViewerMessages = mutableStateOf<List<LoggingEvent>>(emptyList())
    val artifacts = mutableStateOf<List<Artifact>>(emptyList())

    fun clearLogs() {
        logViewerMessages.value = emptyList()
    }

    fun onDownloadArtifact(
        artifact: Artifact,
        file: File,
    ) {
        interactorRunner.launch(
            saveArtifactToFileInteractor,
            SaveArtifactToFileInteractor.Params(artifact.id, file),
        )
    }

    private val _selectedTask = mutableStateOf<Task?>(null)
    val selectedTask: State<Task?>
        get() {
            return _selectedTask
        }
    val userInteractions = mutableStateOf<List<TaskUiModel>>(emptyList())

    private var selectedTaskInScriptInstance = mutableMapOf<ScriptPackageInstance, Task>()
    private var logObservationJob: Job? = null
    private var observedTaskId: String? = null
    private var artifactObservationJob: Job? = null
    private var observedArtifactTaskId: String? = null
    val errorAction = errorResolver.errorAction
    private var tasks: List<Task>? = null
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())

    init {
        ensureDefaultGroupExists()
        observeTaskGroups()
        observeTasks()
    }

    private fun ensureDefaultGroupExists() {
        // Guarantee the user always has at least one group to work with when they haven't created any.
        // This is a deliberate one-shot command on screen entry — NOT a side effect of observing
        // getTaskGroups() — so the observed group list stays a pure read with no feedback loop (#419).
        interactorRunner.launch(getOrCreateDefaultTaskGroupInteractor, Interactor.None()) {}
    }

    fun onTaskSelected(task: Task?) {
        _selectedTask.value = task
        observeLogEventsForTask(task)
        observeArtifactsForTask(task)
        hierarchicalListViewModel.selectedChild?.let {
            if (task == null) {
                selectedTaskInScriptInstance.remove(it)
            } else {
                selectedTaskInScriptInstance.put(it, task)
            }
        }
    }

    fun onCreateTaskGroup() {
        dialogManager.showCreateGroup()
    }

    fun onAddScriptClicked() {
        interactorRunner.launch(getOrCreateDefaultTaskGroupInteractor, Interactor.None()) { group ->
            dialogManager.showSelectingScript(group)
        }
    }

    /**
     * Selects the group, script instance and task that own [taskId] so the Tasks screen lands on the
     * notification's source task. The task list and group children load asynchronously after screen
     * entry, so this polls briefly until the data needed to resolve the task is available.
     */
    fun focusTask(taskId: String) {
        scope.launch {
            repeat(FOCUS_TASK_MAX_ATTEMPTS) {
                if (tryFocusTask(taskId)) return@launch
                delay(FOCUS_TASK_RETRY_DELAY_MS)
            }
        }
    }

    private fun tryFocusTask(taskId: String): Boolean {
        val task = tasks?.firstOrNull { it.id == taskId } ?: return false
        val instanceId = task.scriptInstance.packageInstance.id
        val entry =
            tasksListObserver.childrenByGroupState.entries
                .firstOrNull { (_, items) -> items.any { it.id.id == instanceId } } ?: return false
        val instance = entry.value.first { it.id.id == instanceId }.id

        hierarchicalListViewModel.selectParent(entry.key)
        hierarchicalListViewModel.selectChild(instance)
        onTaskSelected(task)
        return true
    }

    fun openScriptSelectionForPublicIdentifier(publicIdentifier: String) {
        interactorRunner.launch(getOrCreateDefaultTaskGroupInteractor, Interactor.None()) { group ->
            dialogState.value =
                TaskViewState.DialogState.SelectingScript(
                    scriptPackageGroup = group,
                    initialPublicIdentifier = publicIdentifier,
                )
        }
    }

    fun onEditTaskGroup(group: ScriptPackageGroup) {
        dialogManager.showEditGroup(group)
    }

    private fun onDeleteGroup(group: ScriptPackageGroup) {
        val scripts = tasksListObserver.childrenByGroupState[group]?.map { it.id } ?: emptyList()
        if (scripts.isNotEmpty()) {
            dialogManager.showConfirmDeletion(group)
        } else {
            deleteGroupInternal(group)
        }
    }

    fun confirmGroupDeletion(group: ScriptPackageGroup) {
        deleteGroupInternal(group)
    }

    private fun deleteGroupInternal(group: ScriptPackageGroup) {
        tasksActionHandler.deleteGroupInternal(scope, group) {
            // List updates in the UI are handled using flows.
            // Deselect group when the deleted group was the selected one.
            if (group.id == hierarchicalListViewModel.selectedParent?.id) {
                hierarchicalListViewModel.deselectAll()
            }
            closeDialog()
        }
    }

    fun changeGroup(
        scriptPackageInstance: ScriptPackageInstance,
        newGroup: ScriptPackageGroup,
    ) {
        tasksActionHandler.changeGroup(scope, scriptPackageInstance, newGroup) {
            closeDialog()
            // Update UI by selecting the new group
            hierarchicalListViewModel.selectParent(newGroup)
            hierarchicalListViewModel.selectChild(scriptPackageInstance)
        }
    }

    internal fun onDeleteScriptInstance(scriptPackageInstance: ScriptPackageInstance) {
        dialogManager.showConfirmScriptInstanceDeletion(scriptPackageInstance)
    }

    fun confirmScriptInstanceDeletion(scriptPackageInstance: ScriptPackageInstance) {
        tasksActionHandler.deleteScriptInstance(scope, scriptPackageInstance) {
            // List updates in the UI are handled using flows.
            if (scriptPackageInstance.id == hierarchicalListViewModel.selectedChild?.id) {
                hierarchicalListViewModel.selectChild(null)
            }
            closeDialog()
        }
    }

    fun showConfiguration(scriptPackageInstance: ScriptPackageInstance) {
        dialogManager.showConfiguration(scriptPackageInstance)
    }

    fun moveScriptToGroup(scriptInstance: ScriptPackageInstance) {
        val currentGroup = hierarchicalListViewModel.selectedParent
        if (currentGroup != null) {
            val availableGroups = tasksListObserver.childrenByGroupState.keys.toList()
            dialogManager.showMoveScriptToGroup(
                scriptInstance = scriptInstance,
                currentGroup = currentGroup,
                availableGroups = availableGroups,
            )
        }
    }

    fun duplicateScriptInstance(scriptPackageInstance: ScriptPackageInstance) {
        val selectedGroup = hierarchicalListViewModel.selectedParent ?: return
        dialogManager.showDuplicateScript(selectedGroup, scriptPackageInstance)
    }

    fun createGroup(groupName: String) {
        tasksActionHandler.createGroup(scope, groupName) { createdGroup ->
            closeDialog()
            hierarchicalListViewModel.selectParent(createdGroup)
        }
    }

    fun editGroup(groupName: String) {
        val taskGroup = hierarchicalListViewModel.selectedParent
        val taskGroupId = taskGroup?.id ?: return

        tasksActionHandler.editGroup(scope, taskGroupId, groupName) {
            closeDialog()
        }
    }

    fun deleteGroup() {
        val group = hierarchicalListViewModel.selectedParent

        group?.let {
            onDeleteGroup(it)
        }
    }

    fun onScriptInstanceCreated(scriptPackageInstance: ScriptPackageInstance) {
        hierarchicalListViewModel.selectChild(scriptPackageInstance)
    }

    fun startAllTasks() {
        hierarchicalListViewModel.selectedChild?.let {
            tasksActionHandler.startAllTasks(scope, it)
        }
    }

    fun stopAllTasks() {
        hierarchicalListViewModel.selectedChild?.let {
            tasksActionHandler.stopAllTasks(scope, it)
        }
    }

    private var restartErroredJob: Job? = null
    val restartErroredInFlight = mutableStateOf(false)

    fun restartErroredTasks() {
        // Guard against duplicate submissions: the button stays enabled while errored > 0,
        // which only flips once the backend reports the new state, so rapid clicks would
        // otherwise re-fire the same task starts.
        if (restartErroredJob?.isActive == true) return
        val filled = tasksViewState.value as? TaskViewState.DetailViewState.Filled ?: return
        val erroredIds =
            filled.scriptInstances
                .flatMap { it.tasks }
                .filter { it.isError }
                .map { it.id.id }
        if (erroredIds.isEmpty()) return

        restartErroredInFlight.value = true
        restartErroredJob =
            scope.launch {
                try {
                    erroredIds.forEach { id -> tasksActionHandler.startTaskInternal(scope, id) }
                } finally {
                    restartErroredInFlight.value = false
                }
            }
    }

    fun startTask(taskUiModel: TaskUiModel) {
        // Select the task when play button is pressed
        onTaskSelected(taskUiModel.id)

        // Check if task is in finished state and show confirmation dialog
        if (taskUiModel.id.status is TaskStatus.Success) {
            dialogManager.showConfirmTaskRestart(taskUiModel)
            return
        }

        // Start task immediately if not finished
        startTaskInternal(taskUiModel)
    }

    fun confirmTaskRestart(taskUiModel: TaskUiModel) {
        closeDialog()
        // Ensure task remains selected when restarting
        onTaskSelected(taskUiModel.id)
        startTaskInternal(taskUiModel)
    }

    private fun startTaskInternal(taskUiModel: TaskUiModel) {
        tasksActionHandler.startTaskInternal(scope, taskUiModel.id.id)
    }

    fun stopTask(taskUiModel: TaskUiModel) {
        tasksActionHandler.stopTask(scope, taskUiModel.id.id)
    }

    fun closeDialog() {
        dialogManager.closeDialog()
    }

    fun finishUserInteraction(taskUiModel: TaskUiModel) {
        tasksActionHandler.finishUserInteraction(scope, taskUiModel.id.id)
    }

    fun reportIssue(taskUiModel: TaskUiModel) {
        val packageInstance = taskUiModel.id.scriptInstance.packageInstance
        interactorRunner.launch(
            reportScriptIssueInteractor,
            ReportScriptIssueInteractor.Params(
                scriptPackageInstance = packageInstance,
                errorMessage = taskUiModel.message,
                stackTrace = taskUiModel.stackTrace,
            ),
        ) {}
    }

    fun contactSupport() {
        val url =
            hierarchicalListViewModel.selectedChild
                ?.definition
                ?.manifest
                ?.supportUrl ?: return
        interactorRunner.launch(openUrlInteractor, OpenUrlInteractor.Params(url)) {}
    }

    private fun observeTaskGroups() {
        tasksListObserver.observeTaskGroups(scope, hierarchicalListViewModel) { newState ->
            hierarchicalListState.value = newState
        }
    }

    private fun updateUserInteractions() {
        userInteractions.value = TasksViewStateBuilder.extractUserInteractions(tasksViewState.value)
    }

    private fun observeTasks() {
        tasksListObserver.observeTasks(scope) { updatedTasks ->
            tasks = updatedTasks
            tasksFlow.value = updatedTasks ?: emptyList()
            updateTasks(hierarchicalListViewModel.selectedChild)
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun updateTasks(scriptPackageInstance: ScriptPackageInstance?) {
        tasksViewState.value =
            TasksViewStateBuilder.buildDetailViewState(
                tasks,
                scriptPackageInstance,
            )
        updateUserInteractions()
        if (scriptPackageInstance == null) {
            observeLogEventsForTask(null)
            observeArtifactsForTask(null)
        }
    }

    private fun observeLogEventsForTask(task: Task?) {
        if (task?.id == observedTaskId) return
        observedTaskId = task?.id
        logObservationJob?.cancel()
        if (task == null) {
            logViewerMessages.value = emptyList()
            return
        }
        logObservationJob =
            scope.launch(dispatcherProvider.io) {
                combine(
                    logEventRepository.observeLogEvents(task.id),
                    tasksFlow,
                    observeShowDebugLogsInteractor(Interactor.None()).map { result ->
                        when (result) {
                            is SuspendableResult.Success -> result.value
                            is SuspendableResult.Failure -> false
                        }
                    },
                ) { logEvents, allTasks, showDebugLogs ->
                    val currentTask = allTasks.find { it.id == task.id }
                    val merged = mergeLogEventsWithStatusHistory(logEvents, currentTask?.statusHistory ?: emptyList(), task.id)
                    if (showDebugLogs) merged else merged.filter { it.priority != LoggingPriority.DEBUG }
                }.collectLatest { filtered ->
                    withContext(dispatcherProvider.main) {
                        logViewerMessages.value = filtered
                    }
                }
            }
    }

    private fun observeArtifactsForTask(task: Task?) {
        if (task?.id == observedArtifactTaskId) return
        observedArtifactTaskId = task?.id
        artifactObservationJob?.cancel()
        if (task == null) {
            artifacts.value = emptyList()
            return
        }
        artifactObservationJob =
            scope.launch(dispatcherProvider.io) {
                observeArtifactsInteractor(ObserveArtifactsInteractor.Params(task.id))
                    .map { result ->
                        when (result) {
                            is SuspendableResult.Success -> result.value
                            is SuspendableResult.Failure -> emptyList()
                        }
                    }.collectLatest { list ->
                        withContext(dispatcherProvider.main) {
                            artifacts.value = list
                        }
                    }
            }
    }

    @OptIn(ExperimentalTime::class)
    private fun mergeLogEventsWithStatusHistory(
        logEvents: List<LoggingEvent>,
        statusHistory: List<TaskStatus>,
        taskId: String,
    ): List<LoggingEvent> {
        val statusEntries =
            statusHistory
                .filter { it.message != null }
                .map { status ->
                    LoggingEvent(
                        priority =
                            when (status) {
                                is TaskStatus.Error -> LoggingPriority.ERROR
                                is TaskStatus.Running, is TaskStatus.Success -> LoggingPriority.INFO
                                is TaskStatus.Idle -> LoggingPriority.DEBUG
                            },
                        tag = taskId,
                        message = status.message!!,
                        timestamp = Date(status.timestamp.toEpochMilliseconds()),
                    )
                }
        return (logEvents + statusEntries).sortedBy { it.timestamp }
    }

    companion object {
        // The task list and group children load asynchronously after screen entry; poll briefly
        // (up to ~5s) so an "Open task" deep-link from the Notification center can resolve.
        private const val FOCUS_TASK_MAX_ATTEMPTS = 50
        private const val FOCUS_TASK_RETRY_DELAY_MS = 100L
    }
}

package com.cereal.client.presentation.tasks

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.artifact.ObserveArtifactsInteractor
import com.cereal.client.application.interactor.artifact.SaveArtifactToFileInteractor
import com.cereal.client.application.interactor.script.ReportScriptIssueInteractor
import com.cereal.client.application.interactor.settings.OpenUrlInteractor
import com.cereal.client.application.interactor.settings.developers.ObserveShowDebugLogsInteractor
import com.cereal.client.application.interactor.task.GetOrCreateDefaultTaskGroupInteractor
import com.cereal.client.application.interactor.task.UpdateScriptPackageInstanceInteractor
import com.cereal.client.domain.model.artifact.Artifact
import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.logging.LoggingPriority
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.repository.LogEventRepository
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.tasks.model.ScriptInstanceUiModel
import com.cereal.client.presentation.tasks.model.TaskUiModel
import com.cereal.client.presentation.util.InteractorRunner
import com.cereal.client.presentation.view.group.HierarchicalListItem
import com.cereal.client.presentation.view.group.HierarchicalListViewModel
import com.github.kittinunf.result.coroutines.SuspendableResult
import com.github.kittinunf.result.coroutines.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.joinAll
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
    private val lastSelectedTaskSetup: LastSelectedTaskSetup,
    private val updateScriptPackageInstanceInteractor: UpdateScriptPackageInstanceInteractor,
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
                scriptInstance?.let { lastSelectedTaskSetup.id = it.id }
                updateTasks(scriptInstance)
                // Restore this script's last selected task (fresh copy) so logs never show another script's task.
                val remembered = scriptInstance?.let { selectedTaskInScriptInstance[it] }
                onTaskSelected(remembered?.let { r -> tasks?.firstOrNull { it.id == r.id } })
                if (scriptInstance == null) ensureSetupSelected()
            },
            onParentMenuOptionSelected = { group, menuItem ->
                if (menuItem == MenuOption.DELETE) {
                    onDeleteGroup(group)
                } else if (menuItem == MenuOption.EDIT) {
                    onEditTaskGroup(group)
                }
            },
            onChildMenuOptionSelected = { scriptInstance, menuItem -> onSetupMenuOptionSelected(scriptInstance, menuItem) },
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

    /** The VM owns its scope (it's a Koin factory per screen visit), so the screen must release it. */
    fun dispose() {
        scope.cancel()
    }

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

    /** Number of the selected task within its setup, for the Output header ("Output · Task #n"). */
    val selectedTaskNumber: Int?
        get() {
            val selectedId = _selectedTask.value?.id ?: return null
            val filled = tasksViewState.value as? TaskViewState.DetailViewState.Filled ?: return null
            return filled.scriptInstances.firstNotNullOfOrNull { instance ->
                instance.tasks.firstOrNull { it.id.id == selectedId }?.taskNumber
            }
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

    /** "New task setup": opens script selection for the selected setup's group, else the default group. */
    fun onAddScriptClicked() {
        hierarchicalListViewModel.selectedParent?.let {
            dialogManager.showSelectingScript(it)
            return
        }
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

    /** The detail header's "···" menu: the list context menu's setup actions, applied to the selected setup. */
    val headerMenuOptions = listOf(MenuOption.DUPLICATE, MenuOption.MOVE_TO_GROUP, MenuOption.DELETE)

    fun onHeaderMenuOptionSelected(option: MenuOption) {
        hierarchicalListViewModel.selectedChild?.let { onSetupMenuOptionSelected(it, option) }
    }

    private fun onSetupMenuOptionSelected(
        scriptInstance: ScriptPackageInstance,
        option: MenuOption,
    ) {
        if (option == MenuOption.DELETE) {
            onDeleteScriptInstance(scriptInstance)
        } else if (option == MenuOption.VIEW_CONFIGURATION) {
            showConfiguration(scriptInstance)
        } else if (option == MenuOption.DUPLICATE) {
            duplicateScriptInstance(scriptInstance)
        } else if (option == MenuOption.MOVE_TO_GROUP) {
            moveScriptToGroup(scriptInstance)
        }
    }

    /** Edit in the detail header: only while none of the selected setup's tasks runs. */
    val editEnabled: Boolean
        get() =
            hierarchicalListViewModel.selectedChild != null &&
                (tasksViewState.value as? TaskViewState.DetailViewState.Filled)?.runningTasksCount?.let { it == 0 } ?: true

    fun onEditClicked() {
        val selected = hierarchicalListViewModel.selectedChild ?: return
        if (!editEnabled) return
        // Prefer the setup from the observed list, which carries its latest saved configuration.
        val (group, items) =
            tasksListObserver.childrenByGroupState.entries
                .firstOrNull { (_, items) -> items.any { it.id.id == selected.id } } ?: return
        dialogManager.showEditScript(group, items.first { it.id.id == selected.id }.id)
    }

    fun saveEdit(scriptPackageInstance: ScriptPackageInstance) {
        interactorRunner.launch(
            updateScriptPackageInstanceInteractor,
            UpdateScriptPackageInstanceInteractor.Params(scriptPackageInstance),
        ) { closeDialog() }
    }

    fun showConfiguration(scriptPackageInstance: ScriptPackageInstance) {
        dialogManager.showConfiguration(scriptPackageInstance)
    }

    /** The group that actually holds [scriptInstance]; menu actions must not assume it's the selected one. */
    private fun groupOf(scriptInstance: ScriptPackageInstance): ScriptPackageGroup? =
        tasksListObserver.childrenByGroupState.entries
            .firstOrNull { (_, items) -> items.any { it.id.id == scriptInstance.id } }
            ?.key ?: hierarchicalListViewModel.selectedParent

    fun moveScriptToGroup(scriptInstance: ScriptPackageInstance) {
        val currentGroup = groupOf(scriptInstance)
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
        val group = groupOf(scriptPackageInstance) ?: return
        dialogManager.showDuplicateScript(group, scriptPackageInstance)
    }

    fun createGroup(groupName: String) {
        tasksActionHandler.createGroup(scope, groupName) { createdGroup ->
            closeDialog()
            hierarchicalListViewModel.selectParent(createdGroup)
        }
    }

    fun editGroup(
        group: ScriptPackageGroup,
        groupName: String,
    ) {
        tasksActionHandler.editGroup(scope, group.id, groupName) {
            closeDialog()
        }
    }

    fun deleteGroup(group: ScriptPackageGroup) {
        onDeleteGroup(group)
    }

    // A setup the dialog just created; the list may not show it yet, so it is (re)selected once it does.
    private var createdSetupId: String? = null

    /** Selects the new setup without starting it. */
    fun onScriptInstanceCreated(scriptPackageInstance: ScriptPackageInstance) {
        createdSetupId = scriptPackageInstance.id
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

    private val taskFilter = mutableStateOf(TaskFilter.ALL)

    /** Status chip click: filters the task list to that status; clicking the active chip clears the filter. */
    internal fun onStatusChipClicked(filter: TaskFilter) {
        taskFilter.value = if (taskFilter.value == filter) TaskFilter.ALL else filter
    }

    /** Status chips, Start/Stop all toggle and Restart errored for the selected setup; null while it has no tasks. */
    internal val taskControls: State<TaskQuickActionsState?> =
        derivedStateOf {
            val filled = tasksViewState.value as? TaskViewState.DetailViewState.Filled ?: return@derivedStateOf null
            TaskQuickActionsState(
                counts =
                    TaskCounts(
                        idle = filled.idleTasksCount,
                        running = filled.runningTasksCount,
                        finished = filled.finishedTasksCount,
                        errored = filled.erroredTasksCount,
                    ),
                filter = taskFilter.value,
                showStopAll = filled.stopAllTasksEnabled,
                startAllEnabled = filled.startAllTasksEnabled,
                showRestartErrored = filled.erroredTasksCount > 0,
                restartErroredEnabled = !restartErroredInFlight.value,
            )
        }

    /** The selected setup's scripts with their tasks narrowed to the active status filter; scripts left empty are dropped. */
    internal val visibleTasks: State<List<Pair<ScriptInstanceUiModel, List<TaskUiModel>>>> =
        derivedStateOf {
            val filled = tasksViewState.value as? TaskViewState.DetailViewState.Filled ?: return@derivedStateOf emptyList()
            val filter = taskFilter.value
            filled.scriptInstances.mapNotNull { scriptInstance ->
                val tasks = scriptInstance.tasks.filter { filter.matches(it) }
                if (tasks.isEmpty()) null else scriptInstance to tasks
            }
        }

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
                    erroredIds.map { id -> tasksActionHandler.startTaskInternal(scope, id) }.joinAll()
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
            ensureSetupSelected()
        }
    }

    /**
     * A task setup is always selected while any exist: the current one if it still exists, else the one
     * last selected this session, else the first.
     */
    private fun ensureSetupSelected() {
        val setups =
            hierarchicalListViewModel.state.value
                .flatMap { item ->
                    // With a single group the list is flat (#205): setups are top-level children.
                    when (item) {
                        is HierarchicalListItem.Parent -> item.children
                        is HierarchicalListItem.Child -> listOf(item)
                    }
                }.map { it.content.id }
        val current = hierarchicalListViewModel.selectedChild
        val created = setups.firstOrNull { it.id == createdSetupId }?.also { createdSetupId = null }
        val target =
            created
                ?: current?.let { setups.firstOrNull { it.id == current.id } }
                ?: setups.firstOrNull { it.id == lastSelectedTaskSetup.id }
                ?: setups.firstOrNull()
                ?: return
        hierarchicalListViewModel.selectChild(target)
    }

    private fun updateUserInteractions() {
        userInteractions.value = TasksViewStateBuilder.extractUserInteractions(tasks)
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

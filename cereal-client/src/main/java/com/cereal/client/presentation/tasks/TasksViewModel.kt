package com.cereal.client.presentation.tasks

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.ui.unit.Dp
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.artifact.ObserveArtifactsInteractor
import com.cereal.client.application.interactor.artifact.SaveArtifactToFileInteractor
import com.cereal.client.application.interactor.script.ReportScriptIssueInteractor
import com.cereal.client.application.interactor.settings.OpenUrlInteractor
import com.cereal.client.application.interactor.settings.developers.ObserveShowDebugLogsInteractor
import com.cereal.client.application.interactor.task.GetOrCreateDefaultTaskGroupInteractor
import com.cereal.client.application.interactor.task.ObserveTaskSetupSortInteractor
import com.cereal.client.application.interactor.task.SetConcurrentTaskCountInteractor
import com.cereal.client.application.interactor.task.SetTaskSetupSortInteractor
import com.cereal.client.domain.model.artifact.Artifact
import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.logging.LoggingPriority
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskSetupSort
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.repository.LogEventRepository
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.error.handleFailureOrElse
import com.cereal.client.presentation.tasks.model.TaskUiModel
import com.cereal.client.presentation.util.InteractorRunner
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
import kotlinx.coroutines.flow.first
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
    private val setConcurrentTaskCountInteractor: SetConcurrentTaskCountInteractor,
    private val observeTaskSetupSortInteractor: ObserveTaskSetupSortInteractor,
    private val setTaskSetupSortInteractor: SetTaskSetupSortInteractor,
    private val inspectorLayout: TaskInspectorLayoutState,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)
    private val interactorRunner = InteractorRunner(scope, dispatcherProvider, errorResolver)

    // Every group's task setups in list order; null until they first load.
    private val groupedSetups = mutableStateOf<List<Pair<ScriptPackageGroup, List<ScriptPackageInstance>>>?>(null, referentialEqualityPolicy())
    private val allTasks = mutableStateOf<List<Task>>(emptyList())
    private val statusFilter = mutableStateOf(TaskFilter.ALL)
    private val groupFilter = mutableStateOf<String?>(null)

    // Null until the saved sort loads, so the list never first renders in the default order.
    private val setupSort = mutableStateOf<TaskSetupSort?>(null)
    private var loadSortJob: Job? = null
    private var saveSortJob: Job? = null

    // In memory only: every setup starts expanded on each screen visit.
    private val collapsedSetupIds = mutableStateOf(emptySet<String>())

    /**
     * Every task setup under its group, with its tasks. Starts in Loading, not Empty: rendering the empty state before
     * the groups first load would flicker ahead of the real list.
     */
    val listState: State<TaskViewState.ListState> =
        derivedStateOf {
            val sort = setupSort.value ?: return@derivedStateOf TaskViewState.ListState.Loading
            TasksViewStateBuilder.buildListState(
                groupedSetups.value,
                allTasks.value,
                statusFilter.value,
                groupFilter.value,
                collapsedSetupIds.value,
                sort,
            )
        }

    /** A task the list should scroll to (set by [focusTask]); the screen clears it with [onScrolledToTask]. */
    val scrollToTaskId = mutableStateOf<String?>(null)

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

    /** The inspector for the selected task, live; null while no task is selected. */
    val inspector: State<TaskInspectorState?> =
        derivedStateOf { _selectedTask.value?.let { TasksViewStateBuilder.buildInspectorState(it.id, allTasks.value) } }

    /** The inspector width the user dragged to; null until they drag it. */
    val inspectorWidth: State<Dp?> = inspectorLayout.width

    fun onInspectorResized(width: Dp) {
        inspectorLayout.setWidth(width)
    }

    val userInteractions = mutableStateOf<List<TaskUiModel>>(emptyList())

    private var logObservationJob: Job? = null
    private var observedTaskId: String? = null
    private var artifactObservationJob: Job? = null
    private var observedArtifactTaskId: String? = null
    val errorAction = errorResolver.errorAction
    private val tasks: List<Task> get() = allTasks.value
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())

    init {
        ensureDefaultGroupExists()
        observeTaskGroups()
        observeTasks()
        loadSetupSort()
    }

    private fun ensureDefaultGroupExists() {
        // Guarantee the user always has at least one group to work with when they haven't created any.
        // This is a deliberate one-shot command on screen entry — NOT a side effect of observing
        // getTaskGroups() — so the observed group list stays a pure read with no feedback loop (#419).
        interactorRunner.launch(getOrCreateDefaultTaskGroupInteractor, Interactor.None()) {}
    }

    private fun onTaskSelected(task: Task?) {
        _selectedTask.value = task
        observeLogEventsForTask(task)
        observeArtifactsForTask(task)
    }

    /** A task row click opens the inspector for [task], or closes it when [task] is already selected. */
    fun onTaskClicked(task: Task) {
        onTaskSelected(task.takeUnless { it.id == _selectedTask.value?.id })
    }

    fun closeInspector() {
        onTaskSelected(null)
    }

    fun onInspectorAction(action: TaskInspectorAction) {
        val task = inspector.value?.task ?: return
        when (action) {
            TaskInspectorAction.STOP -> stopTask(task)
            TaskInspectorAction.START, TaskInspectorAction.RESTART -> startTask(task)
            TaskInspectorAction.REPORT_ISSUE -> reportIssue(task)
        }
    }

    fun onCreateTaskGroup() {
        dialogManager.showCreateGroup()
    }

    /** "New task setup": opens script selection for the default group. */
    fun onAddScriptClicked() {
        interactorRunner.launch(getOrCreateDefaultTaskGroupInteractor, Interactor.None()) { group ->
            dialogManager.showSelectingScript(group)
        }
    }

    /**
     * Selects [taskId] and scrolls the list to it, so the Tasks screen lands on a notification's source task: its setup
     * is expanded and a status chip or group filter that would hide it is cleared. The
     * tasks and the list load asynchronously after screen entry, so this polls briefly until the task is listed.
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
        val task = tasks.firstOrNull { it.id == taskId } ?: return false
        val (group, setup) = observedSetup(task.scriptInstance.packageInstance.id) ?: return false
        // Land on the task: open its setup and drop any filter that would hide it.
        collapsedSetupIds.value -= setup.id
        if (!statusFilter.value.matches(task.status)) statusFilter.value = TaskFilter.ALL
        if (groupFilter.value != null && groupFilter.value != group.id) groupFilter.value = null
        onTaskSelected(task)
        scrollToTaskId.value = taskId
        return true
    }

    /** A status chip: shows only matching tasks, across every setup. */
    fun onStatusFilterSelected(filter: TaskFilter) {
        statusFilter.value = filter
    }

    /** The group filter: one group's id, or null for all groups. */
    fun onGroupFilterSelected(groupId: String?) {
        groupFilter.value = groupId
    }

    /**
     * The sort dropdown: reorders every group's setups and remembers the choice. Saves run one after another, so the
     * last pick is the one stored.
     */
    fun onSortSelected(sort: TaskSetupSort) {
        // A pick wins over a saved sort still loading.
        loadSortJob?.cancel()
        setupSort.value = sort
        val previousSave = saveSortJob
        saveSortJob =
            scope.launch {
                previousSave?.join()
                interactorRunner.launch(setTaskSetupSortInteractor, SetTaskSetupSortInteractor.Params(sort)).join()
            }
    }

    /** A setup row's chevron or name: collapses or expands its tasks. */
    fun onSetupExpandToggled(setupId: String) {
        val collapsed = collapsedSetupIds.value
        collapsedSetupIds.value = if (setupId in collapsed) collapsed - setupId else collapsed + setupId
    }

    fun onScrolledToTask() {
        scrollToTaskId.value = null
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

    /** A group label's "···" menu. */
    fun onGroupMenuOptionSelected(
        groupId: String,
        option: GroupMenuOption,
    ) {
        val group = groupedSetups.value?.firstOrNull { it.first.id == groupId }?.first ?: return
        when (option) {
            GroupMenuOption.NEW_TASK_SETUP -> dialogManager.showSelectingScript(group)
            GroupMenuOption.RENAME -> dialogManager.showEditGroup(group)
            GroupMenuOption.DELETE -> deleteGroup(group)
        }
    }

    /** Deletes [group] at once when it holds no setups, else asks first. */
    fun deleteGroup(group: ScriptPackageGroup) {
        val setups =
            groupedSetups.value
                ?.firstOrNull { it.first.id == group.id }
                ?.second
                .orEmpty()
        if (setups.isNotEmpty()) {
            dialogManager.showConfirmDeletion(group)
        } else {
            confirmGroupDeletion(group)
        }
    }

    fun confirmGroupDeletion(group: ScriptPackageGroup) {
        // The list follows the change through its flows.
        tasksActionHandler.deleteGroupInternal(scope, group) { closeDialog() }
    }

    fun changeGroup(
        scriptPackageInstance: ScriptPackageInstance,
        newGroup: ScriptPackageGroup,
    ) {
        tasksActionHandler.changeGroup(scope, scriptPackageInstance, newGroup) { closeDialog() }
    }

    fun confirmScriptInstanceDeletion(scriptPackageInstance: ScriptPackageInstance) {
        tasksActionHandler.deleteScriptInstance(scope, scriptPackageInstance) { closeDialog() }
    }

    /** A task setup row's "···" menu; acts on the setup as last observed, which carries its latest saved values. */
    fun onSetupMenuOptionSelected(
        setupId: String,
        option: SetupMenuOption,
    ) {
        val (group, setup) = observedSetup(setupId) ?: return
        when (option) {
            SetupMenuOption.EDIT -> {
                dialogManager.showEditScript(group, setup)
            }

            SetupMenuOption.VIEW_CONFIGURATION -> {
                dialogManager.showConfiguration(setup)
            }

            SetupMenuOption.DUPLICATE -> {
                dialogManager.showDuplicateScript(group, setup)
            }

            SetupMenuOption.MOVE_TO_GROUP -> {
                dialogManager.showMoveScriptToGroup(
                    scriptInstance = setup,
                    currentGroup = group,
                    availableGroups = groupedSetups.value.orEmpty().map { it.first },
                )
            }

            SetupMenuOption.RESTART_FAILED -> {
                restartErroredTasks(setupId)
            }

            SetupMenuOption.CONTACT_SUPPORT -> {
                contactSupport(setup)
            }

            SetupMenuOption.DELETE -> {
                dialogManager.showConfirmScriptInstanceDeletion(setup)
            }
        }
    }

    fun increaseConcurrentTasks(setupId: String) = changeConcurrentTaskCount(setupId, +1)

    fun decreaseConcurrentTasks(setupId: String) = changeConcurrentTaskCount(setupId, -1)

    private fun changeConcurrentTaskCount(
        setupId: String,
        step: Int,
    ) {
        val (_, setup) = observedSetup(setupId) ?: return
        val stepper = TasksViewStateBuilder.stepperOf(setup, tasks) ?: return
        val allowed = if (step > 0) stepper.canIncrease else stepper.canDecrease
        if (!allowed) return
        interactorRunner.launch(
            setConcurrentTaskCountInteractor,
            SetConcurrentTaskCountInteractor.Params(setup, stepper.count + step),
        )
    }

    /** The setup with [id] as last observed in the list, with the group that holds it. */
    private fun observedSetup(id: String): Pair<ScriptPackageGroup, ScriptPackageInstance>? =
        groupedSetups.value.orEmpty().firstNotNullOfOrNull { (group, setups) ->
            setups.firstOrNull { it.id == id }?.let { group to it }
        }

    fun createGroup(groupName: String) {
        tasksActionHandler.createGroup(scope, groupName) { closeDialog() }
    }

    fun editGroup(
        group: ScriptPackageGroup,
        groupName: String,
    ) {
        tasksActionHandler.editGroup(scope, group.id, groupName) {
            closeDialog()
        }
    }

    fun startAllTasks(setupId: String) {
        observedSetup(setupId)?.let { (_, setup) -> tasksActionHandler.startAllTasks(scope, setup) }
    }

    fun stopAllTasks(setupId: String) {
        observedSetup(setupId)?.let { (_, setup) -> tasksActionHandler.stopAllTasks(scope, setup) }
    }

    private var restartErroredJob: Job? = null

    private fun restartErroredTasks(setupId: String) {
        // Guard against duplicate submissions: the button stays enabled while errored > 0,
        // which only flips once the backend reports the new state, so rapid clicks would
        // otherwise re-fire the same task starts.
        if (restartErroredJob?.isActive == true) return
        val erroredIds =
            tasks
                .filter { it.scriptInstance.packageInstance.id == setupId && it.status is TaskStatus.Error }
                .map { it.id }
        if (erroredIds.isEmpty()) return

        restartErroredJob = scope.launch { erroredIds.map { id -> tasksActionHandler.startTaskInternal(scope, id) }.joinAll() }
    }

    /** A row's play button or the inspector's Start/Restart; leaves the selection alone (only a row click selects). */
    fun startTask(taskUiModel: TaskUiModel) {
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

    private fun reportIssue(taskUiModel: TaskUiModel) {
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

    private fun contactSupport(setup: ScriptPackageInstance) {
        val url = setup.definition.manifest.supportUrl ?: return
        interactorRunner.launch(openUrlInteractor, OpenUrlInteractor.Params(url)) {}
    }

    private fun observeTaskGroups() {
        tasksListObserver.observeTaskGroups(scope) { groupedSetups.value = it }
    }

    /** Reads the saved sort once; only this screen changes it, and it already knows its own picks. */
    private fun loadSetupSort() {
        loadSortJob =
            scope.launch {
                val result = withContext(dispatcherProvider.io) { observeTaskSetupSortInteractor(Interactor.None()).first() }
                // On failure, show the list in the default order rather than loading forever.
                setupSort.value = TaskSetupSort.Default
                result.handleFailureOrElse(errorResolver) { setupSort.value = it }
            }
    }

    private fun updateUserInteractions() {
        userInteractions.value = TasksViewStateBuilder.extractUserInteractions(tasks)
    }

    private fun observeTasks() {
        tasksListObserver.observeTasks(scope) { updatedTasks ->
            allTasks.value = updatedTasks.orEmpty()
            tasksFlow.value = updatedTasks.orEmpty()
            updateUserInteractions()
            // The selected task is gone (its setup was deleted, or its count shrank): close the inspector.
            val selectedId = _selectedTask.value?.id
            if (selectedId != null && tasks.none { it.id == selectedId }) onTaskSelected(null)
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

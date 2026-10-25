package com.cereal.client.presentation.tasks

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.artifact.ObserveArtifactsInteractor
import com.cereal.client.application.interactor.artifact.SaveArtifactToFileInteractor
import com.cereal.client.application.interactor.script.ReportScriptIssueInteractor
import com.cereal.client.application.interactor.settings.OpenUrlInteractor
import com.cereal.client.application.interactor.settings.developers.ObserveShowDebugLogsInteractor
import com.cereal.client.application.interactor.task.GetOrCreateDefaultTaskGroupInteractor
import com.cereal.client.application.interactor.task.SetConcurrentTaskCountInteractor
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
    private val setConcurrentTaskCountInteractor: SetConcurrentTaskCountInteractor,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)
    private val interactorRunner = InteractorRunner(scope, dispatcherProvider, errorResolver)

    // Every group's task setups in list order; null until they first load.
    private val groupedSetups = mutableStateOf<List<Pair<ScriptPackageGroup, List<ScriptPackageInstance>>>?>(null, referentialEqualityPolicy())
    private val allTasks = mutableStateOf<List<Task>>(emptyList())

    /**
     * Every task setup under its group, with its tasks. Starts in Loading, not Empty: rendering the empty state before
     * the groups first load would flicker ahead of the real list.
     */
    val listState: State<TaskViewState.ListState> =
        derivedStateOf { TasksViewStateBuilder.buildListState(groupedSetups.value, allTasks.value) }

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
     * Selects [taskId] and scrolls the list to it, so the Tasks screen lands on a notification's source task. The
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
        observedSetup(task.scriptInstance.packageInstance.id) ?: return false
        onTaskSelected(task)
        scrollToTaskId.value = taskId
        return true
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

    fun onEditTaskGroup(group: ScriptPackageGroup) {
        dialogManager.showEditGroup(group)
    }

    /** A group label's "···" menu. */
    fun onGroupMenuOptionSelected(
        group: ScriptPackageGroup,
        option: MenuOption,
    ) {
        when (option) {
            MenuOption.RENAME -> onEditTaskGroup(group)
            MenuOption.DELETE -> onDeleteGroup(group)
            else -> Unit
        }
    }

    private fun onDeleteGroup(group: ScriptPackageGroup) {
        val setups =
            groupedSetups.value
                ?.firstOrNull { it.first.id == group.id }
                ?.second
                .orEmpty()
        if (setups.isNotEmpty()) {
            dialogManager.showConfirmDeletion(group)
        } else {
            deleteGroupInternal(group)
        }
    }

    fun confirmGroupDeletion(group: ScriptPackageGroup) {
        deleteGroupInternal(group)
    }

    private fun deleteGroupInternal(group: ScriptPackageGroup) {
        // The list follows the change through its flows.
        tasksActionHandler.deleteGroupInternal(scope, group) { closeDialog() }
    }

    fun changeGroup(
        scriptPackageInstance: ScriptPackageInstance,
        newGroup: ScriptPackageGroup,
    ) {
        tasksActionHandler.changeGroup(scope, scriptPackageInstance, newGroup) { closeDialog() }
    }

    internal fun onDeleteScriptInstance(scriptPackageInstance: ScriptPackageInstance) {
        dialogManager.showConfirmScriptInstanceDeletion(scriptPackageInstance)
    }

    fun confirmScriptInstanceDeletion(scriptPackageInstance: ScriptPackageInstance) {
        tasksActionHandler.deleteScriptInstance(scope, scriptPackageInstance) { closeDialog() }
    }

    /** A task setup row's "···" menu. */
    fun onSetupMenuOptionSelected(
        scriptInstance: ScriptPackageInstance,
        option: MenuOption,
    ) {
        when (option) {
            MenuOption.DELETE -> onDeleteScriptInstance(scriptInstance)
            MenuOption.VIEW_CONFIGURATION -> showConfiguration(scriptInstance)
            MenuOption.DUPLICATE -> duplicateScriptInstance(scriptInstance)
            MenuOption.MOVE_TO_GROUP -> moveScriptToGroup(scriptInstance)
            MenuOption.EDIT -> onEditClicked(scriptInstance)
            MenuOption.RESTART_FAILED -> restartErroredTasks(scriptInstance)
            MenuOption.CONTACT_SUPPORT -> contactSupport(scriptInstance)
            MenuOption.RENAME -> Unit
        }
    }

    /** A setup is edited only while none of its tasks runs. */
    private fun canEdit(setup: ScriptPackageInstance): Boolean = tasks.none { it.scriptInstance.packageInstance.id == setup.id && it.status is TaskStatus.Running }

    fun onEditClicked(setup: ScriptPackageInstance) {
        if (!canEdit(setup)) return
        // Prefer the setup from the observed list, which carries its latest saved configuration.
        val (group, latest) = observedSetup(setup.id) ?: return
        dialogManager.showEditScript(group, latest)
    }

    fun increaseConcurrentTasks(setup: ScriptPackageInstance) = changeConcurrentTaskCount(setup, +1)

    fun decreaseConcurrentTasks(setup: ScriptPackageInstance) = changeConcurrentTaskCount(setup, -1)

    private fun changeConcurrentTaskCount(
        setup: ScriptPackageInstance,
        step: Int,
    ) {
        val latest = latestOf(setup)
        val stepper = TasksViewStateBuilder.stepperOf(latest, tasks) ?: return
        val allowed = if (step > 0) stepper.canIncrease else stepper.canDecrease
        if (!allowed) return
        interactorRunner.launch(
            setConcurrentTaskCountInteractor,
            SetConcurrentTaskCountInteractor.Params(latest, stepper.count + step),
        )
    }

    /** [setup] as last observed in the list, which carries its latest saved values. */
    private fun latestOf(setup: ScriptPackageInstance): ScriptPackageInstance = observedSetup(setup.id)?.second ?: setup

    /** The setup with [id] as last observed in the list, with the group that holds it. */
    private fun observedSetup(id: String): Pair<ScriptPackageGroup, ScriptPackageInstance>? =
        groupedSetups.value.orEmpty().firstNotNullOfOrNull { (group, setups) ->
            setups.firstOrNull { it.id == id }?.let { group to it }
        }

    fun showConfiguration(scriptPackageInstance: ScriptPackageInstance) {
        dialogManager.showConfiguration(scriptPackageInstance)
    }

    /** The group that holds [scriptInstance]. */
    private fun groupOf(scriptInstance: ScriptPackageInstance): ScriptPackageGroup? = observedSetup(scriptInstance.id)?.first

    fun moveScriptToGroup(scriptInstance: ScriptPackageInstance) {
        val currentGroup = groupOf(scriptInstance)
        if (currentGroup != null) {
            val availableGroups = groupedSetups.value.orEmpty().map { it.first }
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

    fun deleteGroup(group: ScriptPackageGroup) {
        onDeleteGroup(group)
    }

    fun startAllTasks(setup: ScriptPackageInstance) {
        tasksActionHandler.startAllTasks(scope, setup)
    }

    fun stopAllTasks(setup: ScriptPackageInstance) {
        tasksActionHandler.stopAllTasks(scope, setup)
    }

    private var restartErroredJob: Job? = null

    fun restartErroredTasks(setup: ScriptPackageInstance) {
        // Guard against duplicate submissions: the button stays enabled while errored > 0,
        // which only flips once the backend reports the new state, so rapid clicks would
        // otherwise re-fire the same task starts.
        if (restartErroredJob?.isActive == true) return
        val erroredIds =
            tasks
                .filter { it.scriptInstance.packageInstance.id == setup.id && it.status is TaskStatus.Error }
                .map { it.id }
        if (erroredIds.isEmpty()) return

        restartErroredJob = scope.launch { erroredIds.map { id -> tasksActionHandler.startTaskInternal(scope, id) }.joinAll() }
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

    fun contactSupport(setup: ScriptPackageInstance) {
        val url = setup.definition.manifest.supportUrl ?: return
        interactorRunner.launch(openUrlInteractor, OpenUrlInteractor.Params(url)) {}
    }

    private fun observeTaskGroups() {
        tasksListObserver.observeTaskGroups(scope) { groupedSetups.value = it }
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

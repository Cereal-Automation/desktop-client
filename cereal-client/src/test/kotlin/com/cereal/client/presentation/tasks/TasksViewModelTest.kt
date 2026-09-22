package com.cereal.client.presentation.tasks

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.artifact.ObserveArtifactsInteractor
import com.cereal.client.application.interactor.artifact.SaveArtifactToFileInteractor
import com.cereal.client.application.interactor.script.GetScriptsInGroupInteractor
import com.cereal.client.application.interactor.script.ReportScriptIssueInteractor
import com.cereal.client.application.interactor.script.ScriptInstanceInGroup
import com.cereal.client.application.interactor.settings.OpenUrlInteractor
import com.cereal.client.application.interactor.settings.developers.ObserveShowDebugLogsInteractor
import com.cereal.client.application.interactor.task.ChangeScriptPackageInstanceGroupInteractor
import com.cereal.client.application.interactor.task.CreateScriptInstanceGroupInteractor
import com.cereal.client.application.interactor.task.DeleteScriptInstanceInteractor
import com.cereal.client.application.interactor.task.DeleteTaskGroupInteractor
import com.cereal.client.application.interactor.task.EditScriptInstanceGroupInteractor
import com.cereal.client.application.interactor.task.GetOrCreateDefaultTaskGroupInteractor
import com.cereal.client.application.interactor.task.GetTaskGroupsInteractor
import com.cereal.client.application.interactor.task.ObserveTasksInteractor
import com.cereal.client.application.interactor.task.StartAllTasksInScriptPackageInstanceInteractor
import com.cereal.client.application.interactor.task.StartTaskInteractor
import com.cereal.client.application.interactor.task.StopTaskInteractor
import com.cereal.client.application.interactor.task.StopTasksInScriptPackageInstanceInteractor
import com.cereal.client.application.interactor.task.UserInteractionDismissedInteractor
import com.cereal.client.domain.model.artifact.Artifact
import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.logging.LoggingPriority
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.repository.LogEventRepository
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.tasks.model.TaskUiModel
import com.cereal.client.presentation.view.group.GroupListItemContent
import com.cereal.client.presentation.view.group.HierarchicalListItem
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Date
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

// Exercises the many small actions the tasks screen exposes, so the test class
// naturally exceeds the LargeClass threshold.
@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {
    private lateinit var viewModel: TasksViewModel
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)

    // Mocks
    private val errorResolver: ErrorResolver = mockk(relaxed = true)
    private val getTaskGroupsInteractor: GetTaskGroupsInteractor = mockk(relaxed = true)
    private val deleteTaskGroupInteractor: DeleteTaskGroupInteractor = mockk(relaxed = true)
    private val getScriptsInGroupInteractor: GetScriptsInGroupInteractor = mockk(relaxed = true)
    private val observeTasksInteractor: ObserveTasksInteractor = mockk(relaxed = true)

    // Other mocks (relaxed)
    private val getOrCreateDefaultTaskGroupInteractor = mockk<GetOrCreateDefaultTaskGroupInteractor>(relaxed = true)
    private val createScriptInstanceGroupInteractor = mockk<CreateScriptInstanceGroupInteractor>(relaxed = true)
    private val editScriptInstanceGroupInteractor = mockk<EditScriptInstanceGroupInteractor>(relaxed = true)
    private val deleteScriptInstanceInteractor = mockk<DeleteScriptInstanceInteractor>(relaxed = true)
    private val changeScriptPackageInstanceGroupInteractor =
        mockk<ChangeScriptPackageInstanceGroupInteractor>(relaxed = true)
    private val startTaskInteractor = mockk<StartTaskInteractor>(relaxed = true)
    private val stopTaskInteractor = mockk<StopTaskInteractor>(relaxed = true)
    private val stopTasksInScriptPackageInstanceInteractor =
        mockk<StopTasksInScriptPackageInstanceInteractor>(relaxed = true)
    private val startAllTasksInScriptPackageInstanceInteractor =
        mockk<StartAllTasksInScriptPackageInstanceInteractor>(relaxed = true)
    private val userInteractionDismissedInteractor = mockk<UserInteractionDismissedInteractor>(relaxed = true)
    private val reportScriptIssueInteractor = mockk<ReportScriptIssueInteractor>(relaxed = true)
    private val logEventRepository = mockk<LogEventRepository>(relaxed = true)
    private val observeShowDebugLogsInteractor = mockk<ObserveShowDebugLogsInteractor>(relaxed = true)
    private val openUrlInteractor = mockk<OpenUrlInteractor>(relaxed = true)
    private val observeArtifactsInteractor = mockk<ObserveArtifactsInteractor>(relaxed = true)
    private val saveArtifactToFileInteractor = mockk<SaveArtifactToFileInteractor>(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        // Default mock behaviors
        coEvery { getTaskGroupsInteractor(any()) } returns flowOf(SuspendableResult.Success(emptyList()))
        coEvery { observeTasksInteractor(any()) } returns flowOf(SuspendableResult.Success(emptyList()))
        every { logEventRepository.observeLogEvents(any()) } returns flowOf(emptyList<LoggingEvent>())
        coEvery { observeShowDebugLogsInteractor(any()) } returns flowOf(SuspendableResult.Success(false))
        coEvery { observeArtifactsInteractor(any()) } returns flowOf(SuspendableResult.Success(emptyList()))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel = buildViewModel(dispatcherProvider)
    }

    private fun buildViewModel(provider: CoroutinesDispatcherProvider): TasksViewModel {
        val tasksActionHandler =
            TasksActionHandler(
                provider,
                errorResolver,
                createScriptInstanceGroupInteractor,
                editScriptInstanceGroupInteractor,
                deleteTaskGroupInteractor,
                deleteScriptInstanceInteractor,
                changeScriptPackageInstanceGroupInteractor,
                startTaskInteractor,
                stopTaskInteractor,
                stopTasksInScriptPackageInstanceInteractor,
                startAllTasksInScriptPackageInstanceInteractor,
                userInteractionDismissedInteractor,
            )
        val tasksListObserver =
            TasksListObserver(
                provider,
                errorResolver,
                getTaskGroupsInteractor,
                observeTasksInteractor,
                getScriptsInGroupInteractor,
            )

        return TasksViewModel(
            provider,
            getOrCreateDefaultTaskGroupInteractor,
            tasksActionHandler,
            tasksListObserver,
            errorResolver,
            reportScriptIssueInteractor,
            logEventRepository,
            observeShowDebugLogsInteractor,
            openUrlInteractor,
            observeArtifactsInteractor,
            saveArtifactToFileInteractor,
        )
    }

    @Test
    fun `groups list starts in Loading on entry, not Empty, so the empty-state CTA does not flicker`() =
        runTest {
            // Groups exist in the DB, but the observing flow has not emitted yet.
            coEvery { getTaskGroupsInteractor(any()) } returns
                flowOf(SuspendableResult.Success(listOf(ScriptPackageGroup("1", "Group 1"))))

            createViewModel()
            // Deliberately do NOT advance the dispatcher: this reproduces the brief window
            // after the screen mounts but before observeTaskGroups() emits. The init{}
            // coroutines are queued on the StandardTestDispatcher and have not run.

            // Regression: this used to be Empty, which renders the full "create your first
            // group" CTA before the real groups arrive — the entry flicker the user reported.
            assertIs<TaskViewState.GroupViewState.Loading>(viewModel.hierarchicalListState.value)
        }

    @Test
    fun `groups list becomes Filled once groups load`() =
        runTest {
            coEvery { getTaskGroupsInteractor(any()) } returns
                flowOf(SuspendableResult.Success(listOf(ScriptPackageGroup("1", "Group 1"))))

            createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertIs<TaskViewState.GroupViewState.Filled<*, *>>(viewModel.hierarchicalListState.value)
        }

    @Test
    fun `groups list becomes Empty when no groups exist`() =
        runTest {
            coEvery { getTaskGroupsInteractor(any()) } returns
                flowOf(SuspendableResult.Success(emptyList()))

            createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertIs<TaskViewState.GroupViewState.Empty>(viewModel.hierarchicalListState.value)
        }

    @Test
    fun `deleteGroup should delete directly when group has no scripts`() =
        runTest {
            // Arrange
            val group = ScriptPackageGroup("1", "Group 1", 0)
            coEvery { getTaskGroupsInteractor(any()) } returns flowOf(SuspendableResult.Success(listOf(group)))
            coEvery { getScriptsInGroupInteractor(any()) } returns flowOf(SuspendableResult.Success(emptyList()))

            createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            // Select the group
            viewModel.hierarchicalListViewModel.selectParent(group)

            // Act
            viewModel.deleteGroup()
            dispatcher.scheduler.advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { deleteTaskGroupInteractor(any(), any()) }
            assertTrue(viewModel.dialogState.value is TaskViewState.DialogState.Hidden)
        }

    @Test
    fun `deleteGroup should show confirmation dialog when group has scripts`() =
        runTest {
            // Arrange
            val group = ScriptPackageGroup("1", "Group 1", 0)
            val script = mockk<ScriptPackageInstance>(relaxed = true)
            every { script.id } returns "script1"
            val scriptInGroup = ScriptInstanceInGroup(script, 0, 0)

            coEvery { getTaskGroupsInteractor(any()) } returns flowOf(SuspendableResult.Success(listOf(group)))
            coEvery { getScriptsInGroupInteractor(any()) } returns flowOf(SuspendableResult.Success(listOf(scriptInGroup)))

            createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            // Select the group
            viewModel.hierarchicalListViewModel.selectParent(group)

            // Act
            viewModel.deleteGroup()
            dispatcher.scheduler.advanceUntilIdle()

            // Assert
            coVerify(exactly = 0) { deleteTaskGroupInteractor(any(), any()) }
            val dialogState = viewModel.dialogState.value
            assertIs<TaskViewState.DialogState.ConfirmGroupDeletion>(dialogState)
            assertEquals(group, dialogState.group)
        }

    @Test
    fun `confirmGroupDeletion should delete group`() =
        runTest {
            // Arrange
            val group = ScriptPackageGroup("1", "Group 1")
            createViewModel()

            // Act
            viewModel.confirmGroupDeletion(group)
            dispatcher.scheduler.advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { deleteTaskGroupInteractor(any(), any()) }
        }

    @Test
    fun `onDeleteScriptInstance should show confirmation dialog`() =
        runTest {
            // Arrange
            val scriptPackageInstance = mockk<ScriptPackageInstance>(relaxed = true)
            createViewModel()

            // Act
            viewModel.onDeleteScriptInstance(scriptPackageInstance)
            dispatcher.scheduler.advanceUntilIdle()

            // Assert
            coVerify(exactly = 0) { deleteScriptInstanceInteractor(any(), any()) }
            val dialogState = viewModel.dialogState.value
            assertIs<TaskViewState.DialogState.ConfirmScriptInstanceDeletion>(dialogState)
            assertEquals(scriptPackageInstance, dialogState.scriptPackageInstance)
        }

    @Test
    fun `confirmScriptInstanceDeletion should invoke delete interactor`() =
        runTest {
            // Arrange
            val scriptPackageInstance = mockk<ScriptPackageInstance>(relaxed = true)
            createViewModel()

            // Act
            viewModel.confirmScriptInstanceDeletion(scriptPackageInstance)
            dispatcher.scheduler.advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { deleteScriptInstanceInteractor(any(), any()) }
            assertEquals(TaskViewState.DialogState.Hidden, viewModel.dialogState.value)
        }

    @Test
    fun `confirmScriptInstanceDeletion should deselect child when deleted instance is selected`() =
        runTest {
            // Arrange
            val scriptPackageInstance = mockk<ScriptPackageInstance>(relaxed = true)
            every { scriptPackageInstance.id } returns "instance1"
            coEvery { deleteScriptInstanceInteractor(any(), any()) } coAnswers {
                @Suppress("UNCHECKED_CAST")
                val onResult = secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()
                onResult(SuspendableResult.Success(Unit))
            }
            createViewModel()
            viewModel.hierarchicalListViewModel.selectChild(scriptPackageInstance)

            // Act
            viewModel.confirmScriptInstanceDeletion(scriptPackageInstance)
            dispatcher.scheduler.advanceUntilIdle()

            // Assert
            assertEquals(null, viewModel.hierarchicalListViewModel.selectedChild)
        }

    @Test
    fun `closeDialog should hide dialog without invoking delete interactor`() =
        runTest {
            // Arrange
            val scriptPackageInstance = mockk<ScriptPackageInstance>(relaxed = true)
            createViewModel()
            viewModel.onDeleteScriptInstance(scriptPackageInstance)
            dispatcher.scheduler.advanceUntilIdle()
            assertIs<TaskViewState.DialogState.ConfirmScriptInstanceDeletion>(viewModel.dialogState.value)

            // Act
            viewModel.closeDialog()

            // Assert
            coVerify(exactly = 0) { deleteScriptInstanceInteractor(any(), any()) }
            assertEquals(TaskViewState.DialogState.Hidden, viewModel.dialogState.value)
        }

    @Test
    fun `reportIssue should invoke interactor with correct params`() =
        runTest {
            // Arrange
            createViewModel()
            val packageInstance = mockk<ScriptPackageInstance>(relaxed = true)
            val scriptInstance = mockk<ScriptInstance>(relaxed = true)
            every { scriptInstance.packageInstance } returns packageInstance

            val task = mockk<Task>(relaxed = true)
            every { task.scriptInstance } returns scriptInstance

            val taskUiModel =
                TaskUiModel(
                    id = task,
                    taskNumber = 1,
                    isRunning = false,
                    isError = true,
                    isSuccess = false,
                    hasSupportUrl = true,
                    message = "Error occurred",
                    status = "Error",
                    configuration = "",
                    stackTrace = "java.lang.Exception: Error occurred",
                )

            val slot = slot<ReportScriptIssueInteractor.Params>()
            coEvery { reportScriptIssueInteractor(capture(slot), any()) } returns Unit

            // Act
            viewModel.reportIssue(taskUiModel)
            dispatcher.scheduler.advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { reportScriptIssueInteractor(any(), any()) }
            assertEquals(packageInstance, slot.captured.scriptPackageInstance)
            assertEquals("Error occurred", slot.captured.errorMessage)
            assertEquals("java.lang.Exception: Error occurred", slot.captured.stackTrace)
        }

    @Test
    fun `contactSupport should open the selected script's support url`() =
        runTest {
            // Arrange
            createViewModel()
            val instance = mockk<ScriptPackageInstance>(relaxed = true)
            every { instance.definition.manifest.supportUrl } returns "https://github.com/owner/repo/issues"
            viewModel.hierarchicalListViewModel.selectChild(instance)

            val slot = slot<OpenUrlInteractor.Params>()
            coEvery { openUrlInteractor(capture(slot), any()) } returns Unit

            // Act
            viewModel.contactSupport()
            dispatcher.scheduler.advanceUntilIdle()

            // Assert
            coVerify(exactly = 1) { openUrlInteractor(any(), any()) }
            assertEquals("https://github.com/owner/repo/issues", slot.captured.url)
        }

    @Test
    fun `reportIssue should invoke interactor and let it guard when supportUrl is absent`() =
        runTest {
            // Arrange
            createViewModel()
            val packageInstance = mockk<ScriptPackageInstance>(relaxed = true)
            val scriptInstance = mockk<ScriptInstance>(relaxed = true)
            every { scriptInstance.packageInstance } returns packageInstance

            val task = mockk<Task>(relaxed = true)
            every { task.scriptInstance } returns scriptInstance

            val taskUiModel =
                TaskUiModel(
                    id = task,
                    taskNumber = 1,
                    isRunning = false,
                    isError = true,
                    isSuccess = false,
                    hasSupportUrl = true,
                    message = "Error occurred",
                    status = "Error",
                    configuration = "",
                    stackTrace = null,
                )

            coEvery { reportScriptIssueInteractor(any(), any()) } returns Unit

            // Act
            viewModel.reportIssue(taskUiModel)
            dispatcher.scheduler.advanceUntilIdle()

            // Assert — interactor is always called; it decides internally whether to open browser
            coVerify(exactly = 1) { reportScriptIssueInteractor(any(), any()) }
        }

    @Test
    fun `adding script should update group count immediately`() =
        runTest {
            // Arrange
            val group = ScriptPackageGroup("1", "Group 1", 0)
            val groupWithScript = ScriptPackageGroup("1", "Group 1", 1) // Count updated

            // Use a MutableSharedFlow to control emissions
            val taskGroupsFlow =
                kotlinx.coroutines.flow.MutableSharedFlow<SuspendableResult<List<ScriptPackageGroup>, Exception>>(replay = 1)
            taskGroupsFlow.emit(SuspendableResult.Success(listOf(group)))
            coEvery { getTaskGroupsInteractor(any()) } returns taskGroupsFlow

            // Scripts flow that DOES NOT emit initially (simulating delay)
            val scriptsFlow =
                kotlinx.coroutines.flow.MutableSharedFlow<SuspendableResult<List<ScriptInstanceInGroup>, Exception>>()
            coEvery { getScriptsInGroupInteractor(any()) } returns scriptsFlow

            createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            // Initial check
            assertEquals(1, viewModel.hierarchicalListViewModel.state.value.size)
            assertEquals(
                "0",
                (viewModel.hierarchicalListViewModel.state.value[0] as HierarchicalListItem.Parent).content.badge,
            )

            // Act - Update groups (simulate script added)
            taskGroupsFlow.emit(SuspendableResult.Success(listOf(groupWithScript)))
            dispatcher.scheduler.advanceUntilIdle()

            // Assert - Badge should be "1" IMMEDIATELY, even though scriptsFlow hasn't emitted yet
            assertEquals(1, viewModel.hierarchicalListViewModel.state.value.size)
            assertEquals(
                "1",
                (viewModel.hierarchicalListViewModel.state.value[0] as HierarchicalListItem.Parent).content.badge,
            )
        }

    @Test
    fun `observeLogEventsForTask should filter out DEBUG events when showDebugLogs is false`() =
        runTest(UnconfinedTestDispatcher()) {
            val unconfined = UnconfinedTestDispatcher(testScheduler)
            val unconfinedProvider = CoroutinesDispatcherProvider(unconfined, unconfined, unconfined)
            val task = mockk<Task>(relaxed = true)
            every { task.id } returns "task-1"
            every { task.statusHistory } returns emptyList()
            val debugEvent = LoggingEvent(priority = LoggingPriority.DEBUG, tag = "task-1", message = "debug msg", timestamp = Date())
            val infoEvent = LoggingEvent(priority = LoggingPriority.INFO, tag = "task-1", message = "info msg", timestamp = Date())
            every { logEventRepository.observeLogEvents("task-1") } returns flowOf(listOf(debugEvent, infoEvent))
            coEvery { observeShowDebugLogsInteractor(any()) } returns flowOf(SuspendableResult.Success(false))

            val tasksActionHandler =
                TasksActionHandler(
                    unconfinedProvider,
                    errorResolver,
                    createScriptInstanceGroupInteractor,
                    editScriptInstanceGroupInteractor,
                    deleteTaskGroupInteractor,
                    deleteScriptInstanceInteractor,
                    changeScriptPackageInstanceGroupInteractor,
                    startTaskInteractor,
                    stopTaskInteractor,
                    stopTasksInScriptPackageInstanceInteractor,
                    startAllTasksInScriptPackageInstanceInteractor,
                    userInteractionDismissedInteractor,
                )
            val tasksListObserver =
                TasksListObserver(
                    unconfinedProvider,
                    errorResolver,
                    getTaskGroupsInteractor,
                    observeTasksInteractor,
                    getScriptsInGroupInteractor,
                )
            val vm =
                TasksViewModel(
                    unconfinedProvider,
                    getOrCreateDefaultTaskGroupInteractor,
                    tasksActionHandler,
                    tasksListObserver,
                    errorResolver,
                    reportScriptIssueInteractor,
                    logEventRepository,
                    observeShowDebugLogsInteractor,
                    openUrlInteractor,
                    observeArtifactsInteractor,
                    saveArtifactToFileInteractor,
                )

            vm.onTaskSelected(task)

            val messages = vm.logViewerMessages.value
            assertTrue(messages.none { it.priority == LoggingPriority.DEBUG })
            assertTrue(messages.any { it.priority == LoggingPriority.INFO })
        }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `onTaskSelected should expose artifacts for the selected task`() =
        runTest(UnconfinedTestDispatcher()) {
            val unconfined = UnconfinedTestDispatcher(testScheduler)
            val unconfinedProvider = CoroutinesDispatcherProvider(unconfined, unconfined, unconfined)
            val task = mockk<Task>(relaxed = true)
            every { task.id } returns "task-1"
            val artifact = Artifact("a1", "task-1", "report.json", "application/json", 2L, Instant.fromEpochMilliseconds(0))
            coEvery { observeArtifactsInteractor(ObserveArtifactsInteractor.Params("task-1")) } returns
                flowOf(SuspendableResult.Success(listOf(artifact)))
            val vm = buildViewModel(unconfinedProvider)

            vm.onTaskSelected(task)

            assertEquals(listOf(artifact), vm.artifacts.value)
        }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `onDownloadArtifact should invoke the save interactor with the artifact id and destination`() =
        runTest(UnconfinedTestDispatcher()) {
            val unconfined = UnconfinedTestDispatcher(testScheduler)
            val unconfinedProvider = CoroutinesDispatcherProvider(unconfined, unconfined, unconfined)
            val vm = buildViewModel(unconfinedProvider)
            val artifact = Artifact("a1", "task-1", "report.json", null, 2L, Instant.fromEpochMilliseconds(0))
            val destination = File("download.json")

            vm.onDownloadArtifact(artifact, destination)

            coVerify { saveArtifactToFileInteractor(SaveArtifactToFileInteractor.Params("a1", destination), any()) }
        }

    @Test
    fun `observeLogEventsForTask should include DEBUG events when showDebugLogs is true`() =
        runTest(UnconfinedTestDispatcher()) {
            val unconfined = UnconfinedTestDispatcher(testScheduler)
            val unconfinedProvider = CoroutinesDispatcherProvider(unconfined, unconfined, unconfined)
            val task = mockk<Task>(relaxed = true)
            every { task.id } returns "task-1"
            every { task.statusHistory } returns emptyList()
            val debugEvent = LoggingEvent(priority = LoggingPriority.DEBUG, tag = "task-1", message = "debug msg", timestamp = Date())
            val infoEvent = LoggingEvent(priority = LoggingPriority.INFO, tag = "task-1", message = "info msg", timestamp = Date())
            every { logEventRepository.observeLogEvents("task-1") } returns flowOf(listOf(debugEvent, infoEvent))
            coEvery { observeShowDebugLogsInteractor(any()) } returns flowOf(SuspendableResult.Success(true))

            val tasksActionHandler =
                TasksActionHandler(
                    unconfinedProvider,
                    errorResolver,
                    createScriptInstanceGroupInteractor,
                    editScriptInstanceGroupInteractor,
                    deleteTaskGroupInteractor,
                    deleteScriptInstanceInteractor,
                    changeScriptPackageInstanceGroupInteractor,
                    startTaskInteractor,
                    stopTaskInteractor,
                    stopTasksInScriptPackageInstanceInteractor,
                    startAllTasksInScriptPackageInstanceInteractor,
                    userInteractionDismissedInteractor,
                )
            val tasksListObserver =
                TasksListObserver(
                    unconfinedProvider,
                    errorResolver,
                    getTaskGroupsInteractor,
                    observeTasksInteractor,
                    getScriptsInGroupInteractor,
                )
            val vm =
                TasksViewModel(
                    unconfinedProvider,
                    getOrCreateDefaultTaskGroupInteractor,
                    tasksActionHandler,
                    tasksListObserver,
                    errorResolver,
                    reportScriptIssueInteractor,
                    logEventRepository,
                    observeShowDebugLogsInteractor,
                    openUrlInteractor,
                    observeArtifactsInteractor,
                    saveArtifactToFileInteractor,
                )

            vm.onTaskSelected(task)

            val messages = vm.logViewerMessages.value
            assertTrue(messages.any { it.priority == LoggingPriority.DEBUG })
            assertTrue(messages.any { it.priority == LoggingPriority.INFO })
        }

    private fun taskUiModel(
        task: Task,
        message: String = "msg",
    ) = TaskUiModel(
        id = task,
        taskNumber = 1,
        isRunning = false,
        isError = false,
        isSuccess = false,
        hasSupportUrl = false,
        message = message,
        status = "Idle",
        configuration = "",
        stackTrace = null,
    )

    @Test
    fun `onCreateTaskGroup shows the add-group dialog`() {
        createViewModel()

        viewModel.onCreateTaskGroup()

        assertIs<TaskViewState.DialogState.AddingTaskGroup>(viewModel.dialogState.value)
    }

    @Test
    fun `onEditTaskGroup shows the edit-group dialog`() {
        createViewModel()
        val group = ScriptPackageGroup("1", "Group 1")

        viewModel.onEditTaskGroup(group)

        assertIs<TaskViewState.DialogState.EditingTaskGroup>(viewModel.dialogState.value)
    }

    @Test
    fun `showConfiguration shows the configuration dialog`() {
        createViewModel()
        val instance = mockk<ScriptPackageInstance>(relaxed = true)

        viewModel.showConfiguration(instance)

        assertIs<TaskViewState.DialogState.ViewScriptConfiguration>(viewModel.dialogState.value)
    }

    @Test
    fun `clearLogs empties the log viewer messages`() {
        createViewModel()

        viewModel.clearLogs()

        assertTrue(viewModel.logViewerMessages.value.isEmpty())
    }

    @Test
    fun `onScriptInstanceCreated selects the created instance`() {
        createViewModel()
        val instance = mockk<ScriptPackageInstance>(relaxed = true)
        every { instance.id } returns "inst-1"

        viewModel.onScriptInstanceCreated(instance)

        assertEquals(instance, viewModel.hierarchicalListViewModel.selectedChild)
    }

    @Test
    fun `onAddScriptClicked opens script selection for the default group`() =
        runTest {
            val group = ScriptPackageGroup("1", "Group 1")
            coEvery { getOrCreateDefaultTaskGroupInteractor(any(), any()) } coAnswers {
                secondArg<suspend (SuspendableResult<ScriptPackageGroup, Exception>) -> Unit>()(SuspendableResult.Success(group))
            }
            createViewModel()

            viewModel.onAddScriptClicked()
            dispatcher.scheduler.advanceUntilIdle()

            assertIs<TaskViewState.DialogState.SelectingScript>(viewModel.dialogState.value)
        }

    @Test
    fun `openScriptSelectionForPublicIdentifier sets the initial identifier`() =
        runTest {
            val group = ScriptPackageGroup("1", "Group 1")
            coEvery { getOrCreateDefaultTaskGroupInteractor(any(), any()) } coAnswers {
                secondArg<suspend (SuspendableResult<ScriptPackageGroup, Exception>) -> Unit>()(SuspendableResult.Success(group))
            }
            createViewModel()

            viewModel.openScriptSelectionForPublicIdentifier("pkg.id")
            dispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.dialogState.value
            assertIs<TaskViewState.DialogState.SelectingScript>(state)
            assertEquals("pkg.id", state.initialPublicIdentifier)
        }

    @Test
    fun `createGroup invokes the create interactor`() =
        runTest {
            coEvery { createScriptInstanceGroupInteractor(any(), any()) } coAnswers {
                secondArg<suspend (SuspendableResult<ScriptPackageGroup, Exception>) -> Unit>()(
                    SuspendableResult.Success(ScriptPackageGroup("1", "Group 1")),
                )
            }
            createViewModel()

            viewModel.createGroup("Group 1")
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { createScriptInstanceGroupInteractor(any(), any()) }
        }

    @Test
    fun `editGroup invokes the edit interactor when a group is selected`() =
        runTest {
            createViewModel()
            viewModel.hierarchicalListViewModel.selectParent(ScriptPackageGroup("1", "Group 1"))

            viewModel.editGroup("Renamed")
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { editScriptInstanceGroupInteractor(any(), any()) }
        }

    @Test
    fun `duplicateScriptInstance shows the duplicate dialog when a group is selected`() {
        createViewModel()
        viewModel.hierarchicalListViewModel.selectParent(ScriptPackageGroup("1", "Group 1"))
        val instance = mockk<ScriptPackageInstance>(relaxed = true)

        viewModel.duplicateScriptInstance(instance)

        assertIs<TaskViewState.DialogState.DuplicatingScript>(viewModel.dialogState.value)
    }

    @Test
    fun `moveScriptToGroup shows the move dialog when a group is selected`() {
        createViewModel()
        viewModel.hierarchicalListViewModel.selectParent(ScriptPackageGroup("1", "Group 1"))
        val instance = mockk<ScriptPackageInstance>(relaxed = true)

        viewModel.moveScriptToGroup(instance)

        assertIs<TaskViewState.DialogState.MovingScript>(viewModel.dialogState.value)
    }

    @Test
    fun `changeGroup invokes the change-group interactor`() =
        runTest {
            createViewModel()
            val instance = mockk<ScriptPackageInstance>(relaxed = true)
            every { instance.id } returns "inst-1"

            viewModel.changeGroup(instance, ScriptPackageGroup("2", "Group 2"))
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { changeScriptPackageInstanceGroupInteractor(any(), any()) }
        }

    @Test
    fun `startAllTasks and stopAllTasks invoke their interactors when a child is selected`() =
        runTest {
            createViewModel()
            val instance = mockk<ScriptPackageInstance>(relaxed = true)
            every { instance.id } returns "inst-1"
            viewModel.hierarchicalListViewModel.selectChild(instance)

            viewModel.startAllTasks()
            viewModel.stopAllTasks()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { startAllTasksInScriptPackageInstanceInteractor(any(), any()) }
            coVerify(exactly = 1) { stopTasksInScriptPackageInstanceInteractor(any(), any()) }
        }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `startTask starts immediately for a non-finished task`() =
        runTest {
            createViewModel()
            val task = mockk<Task>(relaxed = true)
            every { task.id } returns "task-1"
            every { task.status } returns TaskStatus.Idle(timestamp = Clock.System.now())

            viewModel.startTask(taskUiModel(task))
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { startTaskInteractor(any(), any()) }
        }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `startTask asks for restart confirmation for a finished task`() {
        createViewModel()
        val task = mockk<Task>(relaxed = true)
        every { task.id } returns "task-1"
        every { task.status } returns TaskStatus.Success("done", Clock.System.now())

        viewModel.startTask(taskUiModel(task))

        assertIs<TaskViewState.DialogState.ConfirmTaskRestart>(viewModel.dialogState.value)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `confirmTaskRestart starts the task and closes the dialog`() =
        runTest {
            createViewModel()
            val task = mockk<Task>(relaxed = true)
            every { task.id } returns "task-1"
            every { task.status } returns TaskStatus.Success("done", Clock.System.now())

            viewModel.confirmTaskRestart(taskUiModel(task))
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { startTaskInteractor(any(), any()) }
            assertIs<TaskViewState.DialogState.Hidden>(viewModel.dialogState.value)
        }

    @Test
    fun `stopTask invokes the stop interactor`() =
        runTest {
            createViewModel()
            val task = mockk<Task>(relaxed = true)
            every { task.id } returns "task-1"

            viewModel.stopTask(taskUiModel(task))
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { stopTaskInteractor(any(), any()) }
        }

    @Test
    fun `finishUserInteraction invokes the dismiss interactor`() =
        runTest {
            createViewModel()
            val task = mockk<Task>(relaxed = true)
            every { task.id } returns "task-1"

            viewModel.finishUserInteraction(taskUiModel(task))
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { userInteractionDismissedInteractor.run(any()) }
        }

    private fun parentItem(group: ScriptPackageGroup) =
        HierarchicalListItem.Parent<ScriptPackageGroup, ScriptPackageInstance>(
            content = GroupListItemContent(id = group, title = group.name),
            children = emptyList(),
            expanded = true,
            selected = false,
        )

    private fun childItem(
        group: ScriptPackageGroup,
        instance: ScriptPackageInstance,
    ) = HierarchicalListItem.Child(
        content = GroupListItemContent(id = instance, title = "instance"),
        parentId = group,
        selected = false,
    )

    @Test
    fun `parent menu edit option opens the edit-group dialog`() {
        createViewModel()
        val group = ScriptPackageGroup("1", "Group 1")

        viewModel.hierarchicalListViewModel.onParentMenuOptionClick(parentItem(group), MenuOption.EDIT)

        assertIs<TaskViewState.DialogState.EditingTaskGroup>(viewModel.dialogState.value)
    }

    @Test
    fun `parent menu delete option deletes a group without scripts`() =
        runTest {
            createViewModel()
            val group = ScriptPackageGroup("1", "Group 1")

            viewModel.hierarchicalListViewModel.onParentMenuOptionClick(parentItem(group), MenuOption.DELETE)
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { deleteTaskGroupInteractor(any(), any()) }
        }

    @Test
    fun `child menu view-configuration option opens the configuration dialog`() {
        createViewModel()
        val group = ScriptPackageGroup("1", "Group 1")
        val instance = mockk<ScriptPackageInstance>(relaxed = true)

        viewModel.hierarchicalListViewModel.onChildMenuOptionClick(childItem(group, instance), MenuOption.VIEW_CONFIGURATION)

        assertIs<TaskViewState.DialogState.ViewScriptConfiguration>(viewModel.dialogState.value)
    }

    @Test
    fun `child menu delete option asks for deletion confirmation`() {
        createViewModel()
        val group = ScriptPackageGroup("1", "Group 1")
        val instance = mockk<ScriptPackageInstance>(relaxed = true)

        viewModel.hierarchicalListViewModel.onChildMenuOptionClick(childItem(group, instance), MenuOption.DELETE)

        assertIs<TaskViewState.DialogState.ConfirmScriptInstanceDeletion>(viewModel.dialogState.value)
    }

    @Test
    fun `child menu duplicate and move options open their dialogs when a parent is selected`() {
        createViewModel()
        val group = ScriptPackageGroup("1", "Group 1")
        viewModel.hierarchicalListViewModel.selectParent(group)
        val instance = mockk<ScriptPackageInstance>(relaxed = true)

        viewModel.hierarchicalListViewModel.onChildMenuOptionClick(childItem(group, instance), MenuOption.DUPLICATE)
        assertIs<TaskViewState.DialogState.DuplicatingScript>(viewModel.dialogState.value)

        viewModel.hierarchicalListViewModel.onChildMenuOptionClick(childItem(group, instance), MenuOption.MOVE_TO_GROUP)
        assertIs<TaskViewState.DialogState.MovingScript>(viewModel.dialogState.value)
    }

    @Test
    fun `add-child click opens script selection for the group`() {
        createViewModel()
        val group = ScriptPackageGroup("1", "Group 1")

        viewModel.hierarchicalListViewModel.onAddChildClick(parentItem(group))

        assertIs<TaskViewState.DialogState.SelectingScript>(viewModel.dialogState.value)
    }

    @Test
    fun `selecting and deselecting a group runs the selection callbacks`() {
        createViewModel()
        val group = ScriptPackageGroup("1", "Group 1")

        viewModel.hierarchicalListViewModel.selectParent(group)
        assertEquals(group, viewModel.hierarchicalListViewModel.selectedParent)

        viewModel.hierarchicalListViewModel.deselectAll()
        assertIs<TaskViewState.DetailViewState.NoSelection>(viewModel.tasksViewState.value)
    }

    @Test
    fun `selecting a child builds the detail view state`() {
        createViewModel()
        val group = ScriptPackageGroup("1", "Group 1")
        viewModel.hierarchicalListViewModel.selectParent(group)
        val instance = mockk<ScriptPackageInstance>(relaxed = true)
        every { instance.id } returns "inst-1"

        viewModel.hierarchicalListViewModel.selectChild(instance)

        // updateTasks ran for the selected child (no tasks → not a NoSelection state).
        assertTrue(viewModel.tasksViewState.value !is TaskViewState.DetailViewState.NoSelection)
    }
}

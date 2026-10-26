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
import com.cereal.client.presentation.tasks.script.overview.ScriptRequest
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
            mockk(relaxed = true),
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
            assertIs<TaskViewState.ListState.Loading>(viewModel.listState.value)
        }

    @Test
    fun `groups list becomes Empty when no groups exist`() =
        runTest {
            coEvery { getTaskGroupsInteractor(any()) } returns
                flowOf(SuspendableResult.Success(emptyList()))

            createViewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertIs<TaskViewState.ListState.Empty>(viewModel.listState.value)
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

            // Act
            viewModel.deleteGroup(group)
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

            // Act
            viewModel.deleteGroup(group)
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
                    mockk(relaxed = true),
                )

            vm.onTaskClicked(task)

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

            vm.onTaskClicked(task)

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
                    mockk(relaxed = true),
                )

            vm.onTaskClicked(task)

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
        isIdle = true,
        statusSince = Clock.System.now(),
    )

    @Test
    fun `onCreateTaskGroup shows the add-group dialog`() {
        createViewModel()

        viewModel.onCreateTaskGroup()

        assertIs<TaskViewState.DialogState.AddingTaskGroup>(viewModel.dialogState.value)
    }

    @Test
    fun `clearLogs empties the log viewer messages`() {
        createViewModel()

        viewModel.clearLogs()

        assertTrue(viewModel.logViewerMessages.value.isEmpty())
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
    fun `openScriptSelection sets the initial request`() =
        runTest {
            val group = ScriptPackageGroup("1", "Group 1")
            coEvery { getOrCreateDefaultTaskGroupInteractor(any(), any()) } coAnswers {
                secondArg<suspend (SuspendableResult<ScriptPackageGroup, Exception>) -> Unit>()(SuspendableResult.Success(group))
            }
            createViewModel()

            viewModel.openScriptSelection(ScriptRequest("pkg.id", "variant"))
            dispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.dialogState.value
            assertIs<TaskViewState.DialogState.SelectingScript>(state)
            assertEquals(ScriptRequest("pkg.id", "variant"), state.initialRequest)
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
    fun `editGroup invokes the edit interactor`() =
        runTest {
            createViewModel()

            viewModel.editGroup(ScriptPackageGroup("1", "Group 1"), "Renamed")
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { editScriptInstanceGroupInteractor(any(), any()) }
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
}

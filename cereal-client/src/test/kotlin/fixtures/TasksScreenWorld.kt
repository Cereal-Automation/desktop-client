package fixtures

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.artifact.ObserveArtifactsInteractor
import com.cereal.client.application.interactor.artifact.SaveArtifactToFileInteractor
import com.cereal.client.application.interactor.script.GetScriptsInGroupInteractor
import com.cereal.client.application.interactor.script.ReportScriptIssueInteractor
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
import com.cereal.client.application.script.GitHubIssueUrlBuilder
import com.cereal.client.application.script.ScriptInstanceManager
import com.cereal.client.application.task.JobTaskFactory
import com.cereal.client.application.task.TaskConfigurationBuilder
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.ScopeLinker
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryArtifactRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryCustomDatasetRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLogEventRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemorySystemProvider
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.tasks.LastSelectedTaskSetup
import com.cereal.client.presentation.tasks.TasksActionHandler
import com.cereal.client.presentation.tasks.TasksListObserver
import com.cereal.client.presentation.tasks.TasksViewModel
import kotlinx.coroutines.test.TestDispatcher
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/**
 * The real Tasks screen graph: [TasksViewModel] over real interactors, a real [TaskManager] and
 * in-memory repositories. No mocks; the only stand-in is a no-op [ScopeLinker] (Koin scopes).
 *
 * Seed with [defaultGroup] / [group] / [setup] before calling [viewModel], then advance [dispatcher]
 * (task observation is debounced, so use `advanceUntilIdle()`).
 */
@OptIn(ExperimentalTime::class)
class TasksScreenWorld(
    val dispatcher: TestDispatcher,
) {
    val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    val tasksRepository = InMemoryTasksRepository()
    val scriptInstances = InMemoryScriptInstanceRepository()
    val logEventRepository = InMemoryLogEventRepository()
    val artifactRepository = InMemoryArtifactRepository()
    val systemProvider = InMemorySystemProvider()
    val errorResolver = ErrorResolver()

    /** Session-wide, like its Koin single: shared by every [viewModel] (screen visit) of this world. */
    val lastSelectedTaskSetup = LastSelectedTaskSetup()

    private val noOpScopeLinker =
        object : ScopeLinker {
            override fun linkScriptInstanceToPackage(scriptInstance: ScriptInstance) = Unit

            override fun linkTaskToScriptInstance(task: JobTask) = Unit
        }

    val taskManager =
        TaskManager(
            dispatcherProvider,
            tasksRepository,
            TaskConfigurationBuilder(tasksRepository, InMemoryProxyRepository(), InMemoryCustomDatasetRepository()),
            scriptInstances,
            JobTaskFactory(noOpScopeLinker),
            artifactRepository,
        )

    private val getOrCreateDefaultTaskGroupInteractor = GetOrCreateDefaultTaskGroupInteractor(tasksRepository)

    fun viewModel(): TasksViewModel =
        TasksViewModel(
            dispatcherProvider,
            getOrCreateDefaultTaskGroupInteractor,
            TasksActionHandler(
                dispatcherProvider,
                errorResolver,
                CreateScriptInstanceGroupInteractor(tasksRepository),
                EditScriptInstanceGroupInteractor(tasksRepository),
                DeleteTaskGroupInteractor(tasksRepository, taskManager, scriptInstances),
                DeleteScriptInstanceInteractor(ScriptInstanceManager(taskManager, tasksRepository, scriptInstances)),
                ChangeScriptPackageInstanceGroupInteractor(scriptInstances),
                StartTaskInteractor(taskManager),
                StopTaskInteractor(taskManager),
                StopTasksInScriptPackageInstanceInteractor(taskManager, tasksRepository),
                StartAllTasksInScriptPackageInstanceInteractor(taskManager, scriptInstances),
                UserInteractionDismissedInteractor(tasksRepository),
            ),
            TasksListObserver(
                dispatcherProvider,
                errorResolver,
                GetTaskGroupsInteractor(tasksRepository),
                ObserveTasksInteractor(tasksRepository),
                GetScriptsInGroupInteractor(scriptInstances, tasksRepository),
            ),
            errorResolver,
            ReportScriptIssueInteractor(systemProvider, GitHubIssueUrlBuilder()),
            logEventRepository,
            ObserveShowDebugLogsInteractor(InMemoryApplicationPreferenceRepository()),
            OpenUrlInteractor(systemProvider),
            ObserveArtifactsInteractor(artifactRepository),
            SaveArtifactToFileInteractor(artifactRepository),
            lastSelectedTaskSetup,
        )

    /** The "Default" group, created the same way the screen does on entry. */
    suspend fun defaultGroup(): ScriptPackageGroup = getOrCreateDefaultTaskGroupInteractor.run(Interactor.None())

    suspend fun group(name: String): ScriptPackageGroup = tasksRepository.createScriptInstanceGroup(ScriptPackageGroup(UUID.randomUUID().toString(), name))

    /**
     * Adds a task setup to [group] (the default group when null) with one task per entry of [taskStatuses],
     * numbered in that order (`<id>-task-1`, `<id>-task-2`, ...).
     */
    suspend fun setup(
        id: String,
        group: ScriptPackageGroup? = null,
        vararg taskStatuses: TaskStatus,
    ): ScriptPackageInstance {
        val packageInstance = aScriptPackageInstance(id, "com.example.$id").copy(numberOfConcurrentTasks = taskStatuses.size.coerceAtLeast(1))
        val main = aScriptInstance(packageInstance)
        scriptInstances.addScriptPackageInstance((group ?: defaultGroup()).id, packageInstance, main)
        val createdAt = Clock.System.now()
        tasksRepository.addAllTasks(
            taskStatuses.mapIndexed { index, status ->
                JobTask(
                    id = "$id-task-${index + 1}",
                    scriptInstance = main,
                    configuration = emptyMap(),
                    statusHistory = listOf(status),
                    createdAt = createdAt + index.milliseconds,
                )
            },
        )
        return packageInstance
    }
}

@OptIn(ExperimentalTime::class)
fun idle(): TaskStatus = TaskStatus.Idle(timestamp = Clock.System.now())

@OptIn(ExperimentalTime::class)
fun running(message: String? = null): TaskStatus = TaskStatus.Running(message, Clock.System.now())

@OptIn(ExperimentalTime::class)
fun succeeded(message: String = "Done"): TaskStatus = TaskStatus.Success(message, Clock.System.now())

@OptIn(ExperimentalTime::class)
fun errored(message: String = "Failed"): TaskStatus = TaskStatus.Error(message, timestamp = Clock.System.now())

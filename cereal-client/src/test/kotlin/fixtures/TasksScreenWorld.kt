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
import com.cereal.client.application.interactor.task.ObserveTaskSetupSortInteractor
import com.cereal.client.application.interactor.task.ObserveTasksInteractor
import com.cereal.client.application.interactor.task.SetConcurrentTaskCountInteractor
import com.cereal.client.application.interactor.task.SetTaskSetupSortInteractor
import com.cereal.client.application.interactor.task.StartAllTasksInScriptPackageInstanceInteractor
import com.cereal.client.application.interactor.task.StartTaskInteractor
import com.cereal.client.application.interactor.task.StopTaskInteractor
import com.cereal.client.application.interactor.task.StopTasksInScriptPackageInstanceInteractor
import com.cereal.client.application.interactor.task.UpdateScriptPackageInstanceInteractor
import com.cereal.client.application.interactor.task.UserInteractionDismissedInteractor
import com.cereal.client.application.script.GitHubIssueUrlBuilder
import com.cereal.client.application.script.ScriptInstanceManager
import com.cereal.client.application.script.ScriptLicenseChecker
import com.cereal.client.application.script.TaskSetupLimits
import com.cereal.client.application.task.JobTaskFactory
import com.cereal.client.application.task.TaskConfigurationBuilder
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.ScopeLinker
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.domain.repository.ApplicationPreferenceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryArtifactRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryCustomDatasetRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLogEventRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAuthProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemorySystemProvider
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.tasks.TaskInspectorLayoutState
import com.cereal.client.presentation.tasks.TasksActionHandler
import com.cereal.client.presentation.tasks.TasksListObserver
import com.cereal.client.presentation.tasks.TasksViewModel
import io.mockk.mockk
import kotlinx.coroutines.test.TestDispatcher
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
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
    val preferences: ApplicationPreferenceRepository = InMemoryApplicationPreferenceRepository(),
) {
    val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    val tasksRepository = InMemoryTasksRepository()
    val scriptInstances = InMemoryScriptInstanceRepository()
    val logEventRepository = InMemoryLogEventRepository()
    val artifactRepository = InMemoryArtifactRepository()
    val systemProvider = InMemorySystemProvider()
    val errorResolver = ErrorResolver()

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

    // Each setup is created a second after the previous one, so creation order is never a tie.
    private val firstSetupCreatedAt = Clock.System.now()
    private var setupsCreated = 0

    /** The user's subscriptions; [setup] licenses its script here unless told otherwise. */
    val subscriptions = mutableListOf<Subscription>()

    val taskSetupLimits =
        TaskSetupLimits(ScriptLicenseChecker(InMemoryAuthProvider(subscriptions = subscriptions), InMemoryApplicationPreferenceRepository()))

    val updateScriptPackageInstanceInteractor = UpdateScriptPackageInstanceInteractor(scriptInstances, tasksRepository, taskManager, taskSetupLimits)

    val setConcurrentTaskCountInteractor = SetConcurrentTaskCountInteractor(scriptInstances, tasksRepository, taskManager, taskSetupLimits)

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
            setConcurrentTaskCountInteractor,
            ObserveTaskSetupSortInteractor(preferences),
            SetTaskSetupSortInteractor(preferences),
            TaskInspectorLayoutState(),
        )

    /** The "Default" group, created the same way the screen does on entry. */
    suspend fun defaultGroup(): ScriptPackageGroup = getOrCreateDefaultTaskGroupInteractor.run(Interactor.None())

    suspend fun group(name: String): ScriptPackageGroup = tasksRepository.createScriptInstanceGroup(ScriptPackageGroup(UUID.randomUUID().toString(), name))

    /**
     * Adds a task setup to [group] (the default group when null) with one task per entry of [taskStatuses],
     * numbered in that order (`<id>-task-1`, `<id>-task-2`, ...). Its main [configuration] is empty unless given (a
     * [proxyGroup] makes the task count adjustable); [configurationItems] are the script's configuration definition.
     * Its script is licensed with [capacity]; null leaves it unlicensed. Its script is called [name] and links to [supportUrl].
     */
    suspend fun setup(
        id: String,
        group: ScriptPackageGroup? = null,
        vararg taskStatuses: TaskStatus,
        configuration: ScriptConfigurationValues = emptyMap(),
        configurationItems: List<ScriptConfigurationItemDefinition> = emptyList(),
        capacity: ScriptCapacity? = ScriptCapacity.None,
        name: String = "Test",
        supportUrl: String? = null,
    ): ScriptPackageInstance {
        if (capacity != null) subscriptions += subscription("com.example.$id", capacity)
        val definition =
            aScriptPackage("com.example.$id").let {
                it.copy(
                    manifest = it.manifest.copy(name = name, supportUrl = supportUrl),
                    mainScript = it.mainScript!!.copy(configuration = it.mainScript.configuration.copy(configurationItems = configurationItems)),
                )
            }
        val packageInstance =
            aScriptPackageInstance(id, "com.example.$id").copy(
                mainConfiguration = configuration,
                definition = definition,
                numberOfConcurrentTasks = taskStatuses.size.coerceAtLeast(1),
                createdAt = firstSetupCreatedAt + (setupsCreated++).seconds,
            )
        val main = aScriptInstance(packageInstance).copy(definition = definition.mainScript!!, configuration = configuration)
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

    /** Puts [taskId] in the "waiting for the user" state (a pending Continue button). */
    suspend fun awaitUserInput(taskId: String) = tasksRepository.setUserInteraction(taskId, UserInteraction.ContinueButton(mockk(relaxed = true)))
}

private fun subscription(
    publicIdentifier: String,
    capacity: ScriptCapacity,
) = Subscription(
    id = "sub-$publicIdentifier",
    entitlement =
        ScriptEntitlement(
            publicIdentifier = publicIdentifier,
            title = publicIdentifier,
            latestRelease = null,
            latestDraftRelease = null,
            shortDescription = null,
            price = null,
            capacity = capacity,
        ),
)

@OptIn(ExperimentalTime::class)
fun idle(): TaskStatus = TaskStatus.Idle(timestamp = Clock.System.now())

@OptIn(ExperimentalTime::class)
fun running(message: String? = null): TaskStatus = TaskStatus.Running(message, Clock.System.now())

@OptIn(ExperimentalTime::class)
fun succeeded(message: String = "Done"): TaskStatus = TaskStatus.Success(message, Clock.System.now())

@OptIn(ExperimentalTime::class)
fun errored(message: String = "Failed"): TaskStatus = TaskStatus.Error(message, timestamp = Clock.System.now())

/** A main configuration on a proxy group, so the setup's concurrent-task count sets its number of tasks. */
fun proxyGroup(): ScriptConfigurationValues = mapOf("proxies" to ConfigValue.ProxyGroupValue(ProxyGroup("proxies", "Proxies", 0, emptySequence())))

/** A main configuration on a custom dataset of [records] records, stored under [DATASET_KEY] (see [datasetField]). */
@OptIn(ExperimentalTime::class)
fun dataset(records: Int): ScriptConfigurationValues =
    mapOf(
        DATASET_KEY to
            ConfigValue.CustomDatasetGroupValue(
                CustomDatasetGroup("dataset", "Dataset", records, emptyList(), emptySequence(), Clock.System.now()),
            ),
    )

/** The script's dataset field, which [dataset] fills. */
fun datasetField(): ScriptConfigurationItemDefinition =
    ScriptConfigurationItemDefinition(
        name = "Dataset",
        description = "",
        key = DATASET_KEY,
        position = 0,
        type = ConfigItemType.GroupedConfigItem(items = emptyList()),
        isNullable = false,
        stateModifier = null,
        isScriptIdentifier = false,
    )

private const val DATASET_KEY = "dataset"

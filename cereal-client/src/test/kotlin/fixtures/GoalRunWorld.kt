package fixtures

import com.cereal.client.application.agent.AI_BROWSER_AGENT_PACKAGE
import com.cereal.client.application.interactor.agent.StartGoalRunInteractor
import com.cereal.client.application.interactor.script.StartScriptInteractor
import com.cereal.client.application.interactor.task.GetOrCreateDefaultTaskGroupInteractor
import com.cereal.client.application.interactor.task.StartAllTasksInScriptPackageInstanceInteractor
import com.cereal.client.application.script.ScriptInstanceFactory
import com.cereal.client.application.script.ScriptLicenseChecker
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.ScopeLinker
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAuthProvider
import io.mockk.mockk

/**
 * The real goal-run start graph ([StartGoalRunInteractor] over real interactors and in-memory
 * repositories), licensed for the AI browser agent. Only [taskManager] is a (relaxed) mock: it runs
 * script jars, so it is the external edge.
 */
class GoalRunWorld {
    val tasksRepository = InMemoryTasksRepository()
    val scriptInstances = GroupRecordingScriptInstanceRepository()
    val taskManager = mockk<TaskManager>(relaxed = true)

    private val noOpScopeLinker =
        object : ScopeLinker {
            override fun linkScriptInstanceToPackage(scriptInstance: ScriptInstance) = Unit

            override fun linkTaskToScriptInstance(task: JobTask) = Unit
        }

    val getOrCreateDefaultTaskGroupInteractor = GetOrCreateDefaultTaskGroupInteractor(tasksRepository)

    val startGoalRunInteractor =
        StartGoalRunInteractor(
            getOrCreateDefaultTaskGroupInteractor = getOrCreateDefaultTaskGroupInteractor,
            startScriptInteractor =
                StartScriptInteractor(
                    scriptInstanceRepository = scriptInstances,
                    taskManager = taskManager,
                    scriptLicenseChecker =
                        ScriptLicenseChecker(
                            InMemoryAuthProvider(subscriptions = listOf(agentSubscription())),
                            InMemoryApplicationPreferenceRepository(),
                        ),
                    applicationRepository = InMemoryApplicationRepository(),
                    scriptInstanceFactory = ScriptInstanceFactory(noOpScopeLinker),
                ),
            startAllTasksInScriptPackageInstanceInteractor =
                StartAllTasksInScriptPackageInstanceInteractor(taskManager, scriptInstances),
        )

    private fun agentSubscription() =
        Subscription(
            id = "sub-agent",
            entitlement =
                ScriptEntitlement(
                    publicIdentifier = AI_BROWSER_AGENT_PACKAGE,
                    title = "AI Browser Agent",
                    latestRelease = null,
                    latestDraftRelease = null,
                    shortDescription = null,
                    price = null,
                    capacity = ScriptCapacity.None,
                ),
        )
}

/** [InMemoryScriptInstanceRepository] that also remembers which group each package instance was added to. */
class GroupRecordingScriptInstanceRepository(
    private val delegate: InMemoryScriptInstanceRepository = InMemoryScriptInstanceRepository(),
) : ScriptInstanceRepository by delegate {
    val groupOf = mutableMapOf<String, String>()
    private val mainScripts = mutableMapOf<String, MainScriptInstance>()

    override suspend fun addScriptPackageInstance(
        groupId: String,
        scriptPackageInstance: ScriptPackageInstance,
        mainScriptInstance: MainScriptInstance,
    ) {
        groupOf[scriptPackageInstance.id] = groupId
        mainScripts[scriptPackageInstance.id] = mainScriptInstance
        delegate.addScriptPackageInstance(groupId, scriptPackageInstance, mainScriptInstance)
    }

    override suspend fun getScriptInstances(scriptPackageInstance: ScriptPackageInstance): List<ScriptInstance> = listOfNotNull(mainScripts[scriptPackageInstance.id])
}

package com.cereal.client.application.auth

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.task.JobTaskFactory
import com.cereal.client.application.task.TaskConfigurationBuilder
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.user.User
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.SessionRepository
import com.cereal.client.fixtures.InMemoryScriptInstanceDataSource
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.repository.TasksRepositoryImpl
import com.cereal.sdk.ExecutionResult
import com.cereal.sdk.Script
import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.component.ComponentProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class UserAuthManagerTest {
    /** Runs until cancelled, like a long-lived monitor script. */
    class RunForeverScript : Script<ScriptConfiguration> {
        override suspend fun onStart(
            configuration: ScriptConfiguration,
            provider: ComponentProvider,
        ): Boolean = true

        override suspend fun execute(
            configuration: ScriptConfiguration,
            provider: ComponentProvider,
            statusUpdate: suspend (message: String) -> Unit,
        ): ExecutionResult {
            executing.complete(Unit)
            awaitCancellation()
        }

        override suspend fun onFinish(
            configuration: ScriptConfiguration,
            provider: ComponentProvider,
        ) = Unit

        companion object {
            val executing = CompletableDeferred<Unit>()
        }
    }

    private val user = User("user-1", "name", "email", "key", "token", false)

    private val scriptPackage =
        ScriptPackage(
            source = File("."),
            manifest = mockk(relaxed = true),
            mainScript =
                MainScript(
                    clazz = RunForeverScript::class,
                    configuration = ScriptConfigurationDefinition(ScriptConfiguration::class, emptyList()),
                ),
            childScripts = emptyMap(),
        )

    private val packageInstance =
        ScriptPackageInstance(
            id = "pkg-1",
            mainConfiguration = emptyMap(),
            childConfigurations = emptyMap(),
            definition = scriptPackage,
            createdAt = Clock.System.now(),
            numberOfConcurrentTasks = 1,
        )

    private val scriptInstance =
        MainScriptInstance(
            id = "script-1",
            definition = scriptPackage.mainScript,
            configuration = emptyMap(),
            createdAt = Clock.System.now(),
            packageInstance = packageInstance,
        )

    // Real session + tasks repository over an in-memory data source: persisting a status needs the
    // session user, exactly as in production.
    private val userSession = UserSession()
    private val dataSource = InMemoryScriptInstanceDataSource()
    private val scriptInstanceRepository = mockk<ScriptInstanceRepository>(relaxed = true)
    private val tasksRepository = TasksRepositoryImpl(dataSource, scriptInstanceRepository, userSession)
    private val taskConfigurationBuilder = mockk<TaskConfigurationBuilder>()

    private val sessionRepository =
        object : SessionRepository {
            override suspend fun setSessionUser(user: User?) = userSession.setUser(user)

            override suspend fun getStoredUser(): User? = userSession.getUserFlow().first()

            override suspend fun getAuthenticatedUserFlow(): Flow<User?> = userSession.getUserFlow()
        }

    private val taskManager =
        TaskManager(
            dispatcherProvider = CoroutinesDispatcherProvider(),
            tasksRepository = tasksRepository,
            taskConfigurationBuilder = taskConfigurationBuilder,
            scriptInstanceRepository = scriptInstanceRepository,
            jobTaskFactory = mockk<JobTaskFactory>(),
            artifactRepository = mockk(relaxed = true),
        )

    // Discord, auth and script sync are external edges not exercised by sign-out.
    private val userAuthManager =
        UserAuthManager(
            sessionRepository = sessionRepository,
            authProvider = mockk(),
            discordRepository = mockk(relaxed = true),
            scriptSyncManager = mockk(),
            scriptInstanceRepository = scriptInstanceRepository,
            taskManager = taskManager,
        )

    @BeforeEach
    fun setUp() {
        startKoin {
            modules(
                module {
                    scope(named("UserScope")) { }
                    scope<ScriptPackageInstance> { }
                    scope<Task> { scoped<ComponentProvider> { mockk(relaxed = true) } }
                },
            )
        }
        coEvery { taskConfigurationBuilder.get(any()) } returns emptyList()
        coEvery { scriptInstanceRepository.getScriptInstances(packageInstance) } returns listOf(scriptInstance)
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    // Real dispatchers: the task runs on the IO dispatcher, so virtual time from runTest doesn't apply.
    @Test
    fun `signing out stops running tasks, persists Idle and keeps them for the next sign-in`() =
        runBlocking {
            withTimeout(10_000) {
                sessionRepository.setSessionUser(user)
                tasksRepository.addTask(
                    JobTask(
                        id = "task-1",
                        scriptInstance = scriptInstance,
                        configuration = emptyMap(),
                        statusHistory = emptyList(),
                        createdAt = Clock.System.now(),
                    ),
                )
                taskManager.startTask("task-1")
                RunForeverScript.executing.await()
                val job = tasksRepository.getTask("task-1")?.job

                userAuthManager.deauthenticate()

                assertTrue(job?.isCancelled == true) { "Expected the running job to be cancelled" }
                assertNull(sessionRepository.getStoredUser())
                assertNull(tasksRepository.getTask("task-1"))
                val persisted = dataSource.getJobTasksFromHistory(user, scriptInstance).single()
                assertTrue(persisted.status is TaskStatus.Idle) { "Expected persisted Idle, was ${persisted.status}" }

                // Signing back in restores the persisted task (restoreTasks is what sign-in runs).
                sessionRepository.setSessionUser(user)
                taskManager.restoreTasks(packageInstance)

                val restored = tasksRepository.getTask("task-1")
                assertNotNull(restored)
                assertTrue(restored!!.status is TaskStatus.Idle) { "Expected restored Idle, was ${restored.status}" }
            }
        }
}

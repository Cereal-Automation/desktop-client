package com.cereal.client.application.task

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.agent.AI_BROWSER_AGENT_PACKAGE
import com.cereal.client.application.agent.GoalRunReporter
import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.telemetry.GoalRunFinishReport
import com.cereal.client.domain.model.telemetry.GoalRunOutcome
import com.cereal.client.domain.model.telemetry.GoalRunStartReport
import com.cereal.client.domain.provider.GoalRunReportProvider
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationConfig
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryGoalSharingRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLlmProviderConfigRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryGoalRunReportProvider
import com.cereal.sdk.ExecutionResult
import com.cereal.sdk.Script
import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.component.ComponentProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.math.BigDecimal
import kotlin.reflect.KClass
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** What reaches the [GoalRunReportProvider] when [TaskManager] runs tasks, with the real [GoalRunReporter]. */
@OptIn(ExperimentalTime::class)
class TaskManagerGoalRunReportTest {
    private val tasksRepository = InMemoryTasksRepository()
    private val reports = InMemoryGoalRunReportProvider()
    private val sharing = InMemoryGoalSharingRepository(installId = "install-1")
    private val llm = InMemoryLlmProviderConfigRepository(LlmProviderConfig.of(LlmProviderType.ANTHROPIC, LlmProviderType.ANTHROPIC.defaultBaseUrl, "sk-test", "claude-test"))

    @BeforeEach
    fun setUp() {
        mockkObject(CrashReporter)
        val componentProvider = mockk<ComponentProvider>(relaxed = true)
        startKoin { modules(module { scope<Task> { scoped<ComponentProvider> { componentProvider } } }) }
        StoppableScript.reset()
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
        unmockkObject(CrashReporter)
    }

    private fun taskManager(provider: GoalRunReportProvider = reports) =
        TaskManager(
            dispatcherProvider = CoroutinesDispatcherProvider(),
            tasksRepository = tasksRepository,
            taskConfigurationBuilder = mockk(relaxed = true),
            scriptInstanceRepository = InMemoryScriptInstanceRepository(),
            jobTaskFactory = mockk(relaxed = true),
            artifactRepository = mockk(relaxed = true),
            // Unconfined runs each report inline, so a report has arrived once the task's job has.
            goalRunReporter = GoalRunReporter(provider, sharing, llm, InMemoryApplicationConfig(), CoroutineScope(Dispatchers.Unconfined)),
        )

    private suspend fun TaskManager.run(task: JobTask) {
        tasksRepository.addTask(task)
        startTask(task.id)
        tasksRepository.getTask(task.id)!!.job!!.join()
    }

    @Test
    fun `an agent task reports its start and its success under one run id`() =
        runTest {
            taskManager().run(agentTask(SucceedingScript::class))

            val start = reports.starts.single()
            assertEquals("install-1", start.installId)
            assertEquals("buy the new Jordans in size 44", start.goal)
            assertEquals("https://shop.example.com/jordans", start.startUrl)
            assertEquals("Repeat", start.runMode)
            assertEquals(7L, start.agentVersion)
            assertEquals("2.0.0-test", start.clientVersion)
            assertEquals("ANTHROPIC", start.llmProvider)
            assertEquals("claude-test", start.llmModel)
            val finish = reports.finishes.single()
            assertEquals(start.runId, finish.runId)
            assertEquals(GoalRunOutcome.Succeeded, finish.outcome)
            assertEquals(BigDecimal("0.42"), finish.cost)
        }

    @Test
    fun `a failing agent task reports failed`() =
        runTest {
            taskManager().run(agentTask(FailingScript::class))

            assertEquals(GoalRunOutcome.Failed, reports.finishes.single().outcome)
        }

    @Test
    fun `an agent task stopped by the user reports stopped`() =
        runTest {
            val manager = taskManager()
            val task = agentTask(StoppableScript::class)
            tasksRepository.addTask(task)
            manager.startTask(task.id)
            StoppableScript.started.await()

            manager.stopTask(task.id)
            tasksRepository.getTask(task.id)!!.job!!.join()

            assertEquals(GoalRunOutcome.Stopped, reports.finishes.single().outcome)
        }

    @Test
    fun `a task that isn't the agent reports nothing`() =
        runTest {
            taskManager().run(agentTask(SucceedingScript::class, packageName = "com.example.other"))

            assertTrue(reports.starts.isEmpty())
            assertTrue(reports.finishes.isEmpty())
        }

    @Test
    fun `with sharing off nothing is reported`() =
        runTest {
            sharing.setEnabled(false)

            taskManager().run(agentTask(SucceedingScript::class))

            assertTrue(reports.starts.isEmpty())
            assertTrue(reports.finishes.isEmpty())
        }

    @Test
    fun `turning sharing off mid-run suppresses the finish report`() =
        runTest {
            val manager = taskManager()
            val task = agentTask(StoppableScript::class)
            tasksRepository.addTask(task)
            manager.startTask(task.id)
            StoppableScript.started.await()

            sharing.setEnabled(false)
            manager.stopTask(task.id)
            tasksRepository.getTask(task.id)!!.job!!.join()

            assertEquals(1, reports.starts.size)
            assertTrue(reports.finishes.isEmpty())
        }

    @Test
    fun `sensitive details are masked and other config values are never sent`() =
        runTest {
            val goal =
                "email me at jane.doe@example.com, pay with 4111 1111 1111 1111, call +31 6 12345678, " +
                    "watch HTTPS://shop.example.com/item?ref=abc#top and nike.com/shop?size=44 on 2026-11-01"
            taskManager().run(
                agentTask(
                    SucceedingScript::class,
                    goal = goal,
                    startUrl = "https://shop.example.com/login?token=secret#frag",
                    extraConfig = mapOf("password" to ConfigValue.StringValue("hunter2")),
                ),
            )

            val start = reports.starts.single()
            assertEquals(
                "email me at [email], pay with [card], call [phone], watch HTTPS://shop.example.com/item and nike.com/shop on 2026-11-01",
                start.goal,
            )
            assertEquals("https://shop.example.com/login", start.startUrl)
            assertFalse(start.toString().contains("hunter2"))
        }

    @Test
    fun `a provider that throws doesn't affect the task's status`() =
        runTest {
            val broken =
                object : GoalRunReportProvider {
                    override suspend fun reportStart(report: GoalRunStartReport): Unit = error("down")

                    override suspend fun reportFinish(report: GoalRunFinishReport): Unit = error("down")
                }
            val task = agentTask(SucceedingScript::class)

            taskManager(broken).run(task)

            assertIs<TaskStatus.Success>(tasksRepository.getTask(task.id)!!.status)
        }

    @Test
    fun `a restarted task gets a new run id`() =
        runTest {
            val manager = taskManager()
            val task = agentTask(SucceedingScript::class)
            manager.run(task)

            manager.startTask(task.id)
            tasksRepository.getTask(task.id)!!.job!!.join()

            assertEquals(2, reports.starts.size)
            assertNotEquals(reports.starts[0].runId, reports.starts[1].runId)
            assertEquals(reports.starts.map { it.runId }, reports.finishes.map { it.runId })
        }

    private enum class RunMode { Once, Repeat }

    private fun agentTask(
        scriptClass: KClass<out Script<*>>,
        packageName: String = AI_BROWSER_AGENT_PACKAGE,
        goal: String = "buy the new Jordans in size 44",
        startUrl: String = "https://shop.example.com/jordans",
        extraConfig: Map<String, ConfigValue> = emptyMap(),
    ): JobTask {
        val configDef = ScriptConfigurationDefinition(scriptConfigurationClass = ScriptConfiguration::class, configurationItems = emptyList())
        val scriptPackage =
            ScriptPackage(
                source = File("."),
                manifest = Manifest(packageName = packageName, name = "AI browser agent", versionCode = 7),
                mainScript = MainScript(clazz = scriptClass, configuration = configDef),
                childScripts = emptyMap(),
            )
        val config =
            mapOf(
                "goal" to ConfigValue.StringValue(goal),
                "startUrl" to ConfigValue.StringValue(startUrl),
                "runMode" to ConfigValue.EnumValue(RunMode.Repeat),
            ) + extraConfig
        val packageInstance =
            ScriptPackageInstance(
                id = "pkg",
                mainConfiguration = config,
                childConfigurations = emptyMap(),
                definition = scriptPackage,
                createdAt = Clock.System.now(),
                numberOfConcurrentTasks = 1,
            )
        val scriptInstance =
            MainScriptInstance(
                id = "pkg-main",
                definition = scriptPackage.mainScript!!,
                configuration = config,
                createdAt = Clock.System.now(),
                packageInstance = packageInstance,
            )
        return JobTask(
            id = "task",
            scriptInstance = scriptInstance,
            configuration = config,
            statusHistory = emptyList(),
            createdAt = Clock.System.now(),
        )
    }
}

class SucceedingScript : Script<ScriptConfiguration> {
    override suspend fun onStart(
        configuration: ScriptConfiguration,
        provider: ComponentProvider,
    ) = true

    override suspend fun execute(
        configuration: ScriptConfiguration,
        provider: ComponentProvider,
        statusUpdate: suspend (message: String) -> Unit,
    ): ExecutionResult {
        statusUpdate("Cost: $0.42")
        return ExecutionResult.Success("Done")
    }

    override suspend fun onFinish(
        configuration: ScriptConfiguration,
        provider: ComponentProvider,
    ) = Unit
}

class FailingScript : Script<ScriptConfiguration> {
    override suspend fun onStart(
        configuration: ScriptConfiguration,
        provider: ComponentProvider,
    ) = true

    override suspend fun execute(
        configuration: ScriptConfiguration,
        provider: ComponentProvider,
        statusUpdate: suspend (message: String) -> Unit,
    ): ExecutionResult = ExecutionResult.Error("Couldn't reach the goal")

    override suspend fun onFinish(
        configuration: ScriptConfiguration,
        provider: ComponentProvider,
    ) = Unit
}

class StoppableScript : Script<ScriptConfiguration> {
    override suspend fun onStart(
        configuration: ScriptConfiguration,
        provider: ComponentProvider,
    ) = true

    override suspend fun execute(
        configuration: ScriptConfiguration,
        provider: ComponentProvider,
        statusUpdate: suspend (message: String) -> Unit,
    ): ExecutionResult {
        started.complete(Unit)
        awaitCancellation()
    }

    override suspend fun onFinish(
        configuration: ScriptConfiguration,
        provider: ComponentProvider,
    ) = Unit

    companion object {
        lateinit var started: CompletableDeferred<Unit>

        fun reset() {
            started = CompletableDeferred()
        }
    }
}

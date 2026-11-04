package com.cereal.client.application.task

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.agent.AI_BROWSER_AGENT_PACKAGE
import com.cereal.client.application.agent.FailureReporter
import com.cereal.client.application.agent.GoalRunReporter
import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.application.interactor.settings.privacy.SetShareDiagnosticsEnabledInteractor
import com.cereal.client.domain.model.diagnostics.RUN_TRACE_MIME_TYPE
import com.cereal.client.domain.model.diagnostics.RunTrace
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.provider.FailureReportProvider
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationConfig
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryArtifactRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryDiagnosticsSharingRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLlmProviderConfigRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryRunTraceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryFailureReportProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryGoalRunReportProvider
import com.cereal.client.infrastructure.sdkcomponent.ArtifactComponentImpl
import com.cereal.sdk.ExecutionResult
import com.cereal.sdk.Script
import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.component.ComponentProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Run traces a script emits while [TaskManager] runs it: the real [GoalRunReporter], [FailureReporter] and
 * [ArtifactComponentImpl], over in-memory stores and a clock the test moves.
 */
@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class TaskManagerRunTraceTest {
    private val tasksRepository = InMemoryTasksRepository()
    private val artifacts = InMemoryArtifactRepository()
    private val traces = InMemoryRunTraceRepository()
    private val sharing = InMemoryDiagnosticsSharingRepository()
    private val clock = TestClock()
    private val reports = InMemoryGoalRunReportProvider()
    private val goalRunReporter =
        GoalRunReporter(reports, sharing, InMemoryLlmProviderConfigRepository(), InMemoryApplicationConfig(), CoroutineScope(Dispatchers.Unconfined))
    private val uploads = InMemoryFailureReportProvider()
    private val failureReporter = FailureReporter(traces, goalRunReporter, sharing, uploads, CoroutineScope(Dispatchers.Unconfined), clock)
    private val taskManager =
        TaskManager(
            dispatcherProvider = CoroutinesDispatcherProvider(),
            tasksRepository = tasksRepository,
            taskConfigurationBuilder = mockk(relaxed = true),
            scriptInstanceRepository = InMemoryScriptInstanceRepository(),
            jobTaskFactory = mockk(relaxed = true),
            artifactRepository = artifacts,
            goalRunReporter = goalRunReporter,
            failureReporter = failureReporter,
        )

    @BeforeEach
    fun setUp() {
        mockkObject(CrashReporter)
        startKoin {
            modules(
                module {
                    scope<Task> {
                        scoped<ComponentProvider> {
                            val component = ArtifactComponentImpl(artifacts, failureReporter, getSource<Task>()!!.id)
                            mockk(relaxed = true) { every { artifact() } returns component }
                        }
                    }
                },
            )
        }
        EmittingScript.emits = emptyList()
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
        unmockkObject(CrashReporter)
    }

    @Test
    fun `a run trace never reaches the artifacts while any other emit still does`() =
        runTest {
            EmittingScript.emits = listOf(trace("check-1", "ok", label = "In stock"), Emit("export.csv", "text/csv"))

            run(agentTask())

            assertEquals(listOf("export.csv"), artifacts.observeArtifacts("task").first().map { it.name })
            val check = recentChecks().single()
            assertEquals("check-1", check.key)
            assertEquals("ok", check.outcome)
            assertEquals("In stock", check.label)
        }

    @Test
    fun `a trace is kept with its goal run's id`() =
        runTest {
            EmittingScript.emits = listOf(trace("check-1", "crash"))

            run(agentTask())

            assertEquals(reports.starts.single().runId, recentChecks().single().runId)
        }

    @Test
    fun `a trace from a goal run started with sharing off is kept without a run id`() =
        runTest {
            sharing.setEnabled(false)
            EmittingScript.emits = listOf(trace("check-1", "crash"))

            run(agentTask())

            assertNull(recentChecks().single().runId)
        }

    @Test
    fun `a trace over 1 MB is refused`() =
        runTest {
            EmittingScript.emits = listOf(trace("check-1", "crash", bytes = ByteArray(1024 * 1024 + 1)), trace("check-2", "ok", bytes = ByteArray(1024 * 1024)))

            run(agentTask())

            assertEquals(listOf("check-2"), recentChecks().map { it.key })
            assertTrue(artifacts.observeArtifacts("task").first().isEmpty())
        }

    @Test
    fun `only the last 20 own traces of a task are kept`() =
        runTest {
            sharing.setEnabled(false)
            EmittingScript.emits = (1..25).map { trace("check-$it", "ok") }

            run(agentTask())

            assertEquals((25 downTo 6).map { "check-$it" }, recentChecks().map { it.key })
        }

    @Test
    fun `failed traces awaiting upload are kept beyond the last 20, at most 5 per goal run`() =
        runTest {
            EmittingScript.emits = (1..6).map { trace("check-$it", "crash") } + (7..30).map { trace("check-$it", "ok") }

            run(agentTask())

            val kept = recentChecks().map { it.key }
            assertEquals((30 downTo 11).map { "check-$it" } + (5 downTo 1).map { "check-$it" }, kept)
        }

    @Test
    fun `notable traces are kept beyond the last 20`() =
        runTest {
            EmittingScript.emits = listOf(trace("check-1", "ok", notable = true)) + (2..25).map { trace("check-$it", "ok") }

            run(agentTask())

            assertEquals("check-1", recentChecks().last().key)
            assertEquals(21, recentChecks().size)
        }

    @Test
    fun `every trace goes after 7 days`() =
        runTest {
            EmittingScript.emits = listOf(trace("check-1", "crash"), trace("check-2", "ok", notable = true), trace("check-3", "ok"))
            run(agentTask())
            clock.advance(7.days)

            EmittingScript.emits = listOf(trace("check-1", "ok"))
            run(agentTask())

            assertEquals(1, recentChecks().size)
            assertEquals(1, traces.bytes.size)
        }

    @Test
    fun `deleting a task removes its traces`() =
        runTest {
            EmittingScript.emits = listOf(trace("check-1", "crash"), trace("check-2", "ok"))
            val task = agentTask()
            run(task)

            taskManager.deleteTask(task)

            assertTrue(recentChecks().isEmpty())
            assertTrue(traces.bytes.isEmpty())
        }

    @Test
    fun `a learned trace is never listed and goes with its own trace`() =
        runTest {
            sharing.setEnabled(false)
            EmittingScript.emits = listOf(trace("check-1", "crash", learned = true), trace("check-1", "crash"))
            run(agentTask())
            assertEquals(listOf("check-1"), recentChecks().map { it.key })
            assertEquals(2, traces.getAll().size)

            EmittingScript.emits = (2..21).map { trace("check-$it", "ok") }
            run(agentTask())

            assertTrue(traces.getAll().none { it.key == "check-1" })
        }

    @Test
    fun `a failed check uploads right away with its goal run's id and the learned trace emitted before it`() =
        runTest {
            EmittingScript.emits =
                listOf(
                    trace("check-1", "ok"),
                    trace("check-2", "crash", learned = true, bytes = "learned".toByteArray()),
                    trace("check-2", "crash", bytes = "own".toByteArray()),
                )

            run(agentTask())

            val report = uploads.uploads.single()
            assertEquals(reports.starts.single().runId, report.runId)
            assertEquals("check-2", report.key)
            assertEquals("crash", report.outcome)
            assertNull(report.note)
            assertEquals("own", report.trace.decodeToString())
            assertEquals("learned", report.learnedTrace?.decodeToString())
        }

    @Test
    fun `a failed check without a learned trace uploads its own alone`() =
        runTest {
            EmittingScript.emits = listOf(trace("check-1", "fail"))

            run(agentTask())

            assertNull(uploads.uploads.single().learnedTrace)
        }

    @Test
    fun `a check from a goal run started with sharing off never uploads`() =
        runTest {
            sharing.setEnabled(false)
            EmittingScript.emits = listOf(trace("check-1", "crash"))
            run(agentTask())

            sharing.setEnabled(true)
            failureReporter.resumePendingUploads()

            assertTrue(uploads.uploads.isEmpty())
        }

    @Test
    fun `a registered report deletes the trace's bytes, shows reported and is never sent again`() =
        runTest {
            EmittingScript.emits = listOf(trace("check-1", "crash", learned = true), trace("check-1", "crash"))
            run(agentTask())

            failureReporter.resumePendingUploads()

            assertEquals(1, uploads.uploads.size)
            assertEquals(RunTrace.UploadState.Reported, recentChecks().single().uploadState)
            assertTrue(traces.bytes.isEmpty())
        }

    @Test
    fun `a report the server can't take now is kept and retried until it's 7 days old`() =
        runTest {
            uploads.answer = FailureReportProvider.Result.Retry
            EmittingScript.emits = listOf(trace("check-1", "crash"))
            run(agentTask())

            failureReporter.resumePendingUploads()
            assertEquals(2, uploads.uploads.size)
            assertEquals(RunTrace.UploadState.Pending, recentChecks().single().uploadState)

            uploads.answer = FailureReportProvider.Result.Registered
            clock.advance(7.days)
            failureReporter.resumePendingUploads()

            assertEquals(2, uploads.uploads.size)
            assertTrue(recentChecks().isEmpty())
        }

    @Test
    fun `a dropped report is never retried`() =
        runTest {
            uploads.answer = FailureReportProvider.Result.Drop
            EmittingScript.emits = listOf(trace("check-1", "crash"))
            run(agentTask())

            failureReporter.resumePendingUploads()

            assertEquals(1, uploads.uploads.size)
            assertEquals(RunTrace.UploadState.Dropped, recentChecks().single().uploadState)
        }

    @Test
    fun `turning sharing off takes pending reports back for good`() =
        runTest {
            uploads.answer = FailureReportProvider.Result.Retry
            EmittingScript.emits = listOf(trace("check-1", "crash"))
            run(agentTask())
            val setSharing = SetShareDiagnosticsEnabledInteractor(sharing, failureReporter)

            setSharing.run(SetShareDiagnosticsEnabledInteractor.Params(false))
            setSharing.run(SetShareDiagnosticsEnabledInteractor.Params(true))
            uploads.answer = FailureReportProvider.Result.Registered
            failureReporter.resumePendingUploads()

            assertEquals(1, uploads.uploads.size)
            assertEquals(RunTrace.UploadState.None, recentChecks().single().uploadState)
        }

    @Test
    fun `sharing turned off before an upload step stops it`() =
        runTest {
            uploads.answer = FailureReportProvider.Result.Retry
            EmittingScript.emits = listOf(trace("check-1", "crash"))
            run(agentTask())

            sharing.setEnabled(false)
            failureReporter.resumePendingUploads()
            sharing.setEnabled(true)
            failureReporter.resumePendingUploads()

            assertEquals(1, uploads.uploads.size)
            assertEquals(RunTrace.UploadState.None, recentChecks().single().uploadState)
        }

    @Test
    fun `at most 5 failure reports go per goal run`() =
        runTest {
            EmittingScript.emits = (1..7).map { trace("check-$it", "crash") }

            run(agentTask())
            failureReporter.resumePendingUploads()

            assertEquals((1..5).map { "check-$it" }, uploads.uploads.map { it.key })
        }

    @Test
    fun `pending reports resume when the app starts with a user signed in, then hourly`() =
        runTest {
            uploads.answer = FailureReportProvider.Result.Retry
            EmittingScript.emits = listOf(trace("check-1", "crash"))
            run(agentTask())
            val user = MutableStateFlow<Any?>(null)
            val restarted = FailureReporter(traces, goalRunReporter, sharing, uploads, backgroundScope, clock)

            restarted.start(user)
            runCurrent()
            assertEquals(1, uploads.uploads.size)

            user.value = "user"
            runCurrent()
            assertEquals(2, uploads.uploads.size)

            advanceTimeBy(1.hours)
            runCurrent()
            assertEquals(3, uploads.uploads.size)
        }

    private suspend fun recentChecks(taskId: String = "task"): List<RunTrace> = failureReporter.observeRecentChecks(taskId).first()

    private suspend fun run(task: JobTask) {
        if (tasksRepository.getTask(task.id) == null) tasksRepository.addTask(task)
        taskManager.startTask(task.id)
        tasksRepository.getTask(task.id)!!.job!!.join()
    }

    private fun agentTask(id: String = "task"): JobTask {
        val configDef = ScriptConfigurationDefinition(scriptConfigurationClass = ScriptConfiguration::class, configurationItems = emptyList())
        val scriptPackage =
            ScriptPackage(
                source = File("."),
                manifest = Manifest(packageName = AI_BROWSER_AGENT_PACKAGE, name = "AI browser agent", versionCode = 7),
                mainScript = MainScript(clazz = EmittingScript::class, configuration = configDef),
                childScripts = emptyMap(),
            )
        val config = mapOf("goal" to ConfigValue.StringValue("watch the Jordans"), "startUrl" to ConfigValue.StringValue("https://shop.example.com"))
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
        return JobTask(id = id, scriptInstance = scriptInstance, configuration = config, statusHistory = emptyList(), createdAt = Clock.System.now())
    }

    private class TestClock(
        var now: Instant = Instant.parse("2026-11-04T12:00:00Z"),
    ) : Clock {
        /** Each reading moves a second on, so traces received in one run never tie. */
        override fun now(): Instant = now.also { now += 1.seconds }

        fun advance(by: Duration) {
            now += by
        }
    }
}

class Emit(
    val name: String,
    val mimeType: String?,
    val bytes: ByteArray = ByteArray(16),
)

fun trace(
    key: String,
    outcome: String,
    label: String = "label",
    notable: Boolean = false,
    learned: Boolean = false,
    bytes: ByteArray = ByteArray(16),
) = Emit(
    name = (if (learned) "learned-trace-" else "trace-") + "$key.jsonl.gz",
    mimeType = "$RUN_TRACE_MIME_TYPE; key=$key; outcome=$outcome; label=$label" + if (notable) "; notable" else "",
    bytes = bytes,
)

class EmittingScript : Script<ScriptConfiguration> {
    override suspend fun onStart(
        configuration: ScriptConfiguration,
        provider: ComponentProvider,
    ) = true

    override suspend fun execute(
        configuration: ScriptConfiguration,
        provider: ComponentProvider,
        statusUpdate: suspend (message: String) -> Unit,
    ): ExecutionResult {
        emits.forEach { provider.artifact().emit(it.name, it.bytes, it.mimeType) }
        return ExecutionResult.Success("Done")
    }

    override suspend fun onFinish(
        configuration: ScriptConfiguration,
        provider: ComponentProvider,
    ) = Unit

    companion object {
        @Volatile
        var emits: List<Emit> = emptyList()
    }
}

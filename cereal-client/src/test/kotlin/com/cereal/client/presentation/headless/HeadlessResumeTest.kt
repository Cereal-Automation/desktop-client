package com.cereal.client.presentation.headless

import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.notification.SendRestartReportInteractor
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.ScopeLinker
import com.cereal.client.domain.model.notification.DiscordNotificationData
import com.cereal.client.domain.model.script.ChildScript
import com.cereal.client.domain.model.script.ChildScriptInstance
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.provider.AppUpdateProvider
import com.cereal.client.domain.provider.NotificationProvider
import com.cereal.client.domain.repository.NotificationSettingsRepository
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAppUpdateProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryNotificationProvider
import com.cereal.sdk.ExecutionResult
import com.cereal.sdk.Script
import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.component.ComponentProvider
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.Koin
import testutil.HeadlessTestScope
import testutil.runHeadlessTest
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.measureTime

@OptIn(ExperimentalTime::class)
class HeadlessResumeTest {
    private val group = ScriptPackageGroup("g-alpha", "Alpha")
    private val scriptDefinition = ScriptConfigurationDefinition(ScriptConfiguration::class, emptyList())
    private val pkg =
        ScriptPackageInstance(
            "pkg-monitor",
            emptyMap(),
            emptyMap(),
            ScriptPackage(
                source = File("."),
                manifest = Manifest(packageName = "com.example.monitor", name = "Monitor", versionCode = 1),
                mainScript = MainScript(HeadlessTasksTest.RunForeverScript::class, scriptDefinition),
                childScripts = emptyMap(),
            ),
            Clock.System.now(),
            numberOfConcurrentTasks = 1,
        )
    private val script = MainScriptInstance("script-monitor", pkg.definition.mainScript, emptyMap(), pkg.createdAt, pkg)
    private val child =
        ChildScriptInstance(
            "script-child",
            ChildScript("child", HeadlessTasksTest.RunForeverScript::class, scriptDefinition),
            emptyMap(),
            pkg.createdAt,
            pkg,
            script,
            null,
        )
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)
    private val running = TaskStatus.Running("Working", t0)

    private fun task(
        id: String,
        status: TaskStatus,
        scriptInstance: ScriptInstance = script,
    ) = JobTask(id, scriptInstance, emptyMap(), listOf(TaskStatus.Idle(timestamp = t0), status), null, t0)

    /** Seeds [tasks] as the previous process left them, before boot restores them; Discord is on for the report. */
    private fun seeded(
        vararg tasks: JobTask,
        extra: suspend Koin.() -> Unit = {},
    ): suspend Koin.() -> Unit =
        {
            get<NotificationSettingsRepository>().setDiscordWebhookEnabled(true)
            get<NotificationSettingsRepository>().setDiscordWebhookUrl("https://discord.com/api/webhooks/1/global")
            get<TasksRepository>().createScriptInstanceGroup(group)
            val linker = get<ScopeLinker>()
            linker.linkScriptInstanceToPackage(script)
            get<ScriptInstanceRepository>().addScriptPackageInstance(group.id, pkg, script)
            get<ScriptInstanceRepository>().addChildScriptInstance(script, "child", child)
            tasks.forEach(linker::linkTaskToScriptInstance)
            get<TasksRepository>().addAllTasks(tasks.toList())
            extra()
        }

    private suspend fun HeadlessTestScope.status(id: String) = get<TasksRepository>().getTask(id)!!.status

    private val HeadlessTestScope.reports
        get() =
            (get<NotificationProvider>() as InMemoryNotificationProvider)
                .sent
                .map { (it as DiscordNotificationData).content!! }
                .filter { SendRestartReportInteractor.TITLE in it }

    private suspend fun HeadlessTestScope.awaitReports(count: Int): List<String> {
        val deadline = System.currentTimeMillis() + 2.seconds.inWholeMilliseconds
        while (reports.size < count) {
            check(System.currentTimeMillis() < deadline) { "reports: $reports" }
            delay(10)
        }
        return reports
    }

    @Test
    fun `exactly the main-script tasks left Running resume, children go back to Idle`() =
        runHeadlessTest(
            seed = seeded(task("t1", running), task("t2", TaskStatus.Idle("Stopped", t0)), task("c1", running, child)),
        ) {
            awaitText("1-5 tabs")
            awaitText("1 running")

            val t1 = get<TasksRepository>().getTask("t1")!!
            assertEquals("Resumed after restart", t1.statusHistory.first { it.timestamp > t0 }.message)
            assertTrue(t1.job!!.isActive)
            assertEquals("Stopped", status("t2").message)
            assertNull(get<TasksRepository>().getTask("t2")!!.job)
            val c1 = status("c1")
            assertTrue(c1 is TaskStatus.Idle, c1.toString())
            assertNull(get<TasksRepository>().getTask("c1")!!.job)

            assertEquals(listOf("**Headless mode restarted**\nHeadless mode on test-host restarted. Resumed 1 task(s)."), awaitReports(1))
        }

    @Test
    fun `a task failing its pre-start check lands on Idle with the reason, and the report lists it`() =
        // One task per script instance may run, so the second one left Running can't resume.
        runHeadlessTest(seed = seeded(task("t1", running), task("t2", running))) {
            awaitText("1-5 tabs")
            awaitText("1 running")

            val reason = "Couldn't resume after restart: The maximum number of concurrent tasks is reached: 1"
            assertEquals(TaskStatus.Idle::class, status("t2")::class)
            assertEquals(reason, status("t2").message)
            assertEquals(
                listOf(
                    "**Headless mode restarted**\nHeadless mode on test-host restarted. Resumed 1 task(s). " +
                        "Couldn't resume 1 task(s): Monitor: The maximum number of concurrent tasks is reached: 1.",
                ),
                awaitReports(1),
            )

            // One report per boot: a later sign-in sends nothing.
            get<SendRestartReportInteractor>()(Interactor.None())
            delay(200)
            assertEquals(1, reports.size)
        }

    @Test
    fun `SIGTERM leaves the task Running persisted`() =
        runHeadlessTest(seed = seeded(task("t1", running))) {
            awaitText("1 running")
            val job = get<TasksRepository>().getTask("t1")!!.job!!

            get<TaskManager>().shutdown()

            assertTrue(job.isCompleted)
            assertEquals("Resumed after restart", status("t1").message)
            assertTrue(status("t1") is TaskStatus.Running)
        }

    @Test
    fun `an explicit stop persists Idle, so the task won't resume`() =
        runHeadlessTest(seed = seeded(task("t1", running))) {
            awaitText("1 running")
            val job = get<TasksRepository>().getTask("t1")!!.job!!

            get<TaskManager>().stopTask("t1")
            job.join()

            assertTrue(status("t1") is TaskStatus.Idle, status("t1").toString())
        }

    @Test
    fun `nothing resumes while a required update blocks`() =
        runHeadlessTest(
            seed =
                seeded(task("t1", running)) {
                    (get<AppUpdateProvider>() as InMemoryAppUpdateProvider).latestVersion =
                        InMemoryAppUpdateProvider.version("2.0.0", "2.0.0")
                },
        ) {
            awaitText("No tasks can start on this version.")
            delay(200)
            assertEquals("Working", status("t1").message)
            assertNull(get<TasksRepository>().getTask("t1")!!.job)
            assertEquals(emptyList<String>(), reports)
        }

    @Test
    fun `stopping all tasks gives up waiting on a script that ignores cancellation`() {
        StubbornScript.released = false
        val stubbornPkg =
            ScriptPackageInstance(
                "pkg-stubborn",
                emptyMap(),
                emptyMap(),
                ScriptPackage(
                    source = File("."),
                    manifest = Manifest(packageName = "com.example.stubborn", name = "Stubborn", versionCode = 1),
                    mainScript = MainScript(StubbornScript::class, scriptDefinition),
                    childScripts = emptyMap(),
                ),
                Clock.System.now(),
                numberOfConcurrentTasks = 1,
            )
        val stubborn = MainScriptInstance("script-stubborn", stubbornPkg.definition.mainScript, emptyMap(), stubbornPkg.createdAt, stubbornPkg)
        val t1 = task("t1", TaskStatus.Idle(timestamp = t0), stubborn)
        runHeadlessTest(
            seed = {
                get<TasksRepository>().createScriptInstanceGroup(group)
                val linker = get<ScopeLinker>()
                linker.linkScriptInstanceToPackage(stubborn)
                get<ScriptInstanceRepository>().addScriptPackageInstance(group.id, stubbornPkg, stubborn)
                linker.linkTaskToScriptInstance(t1)
                get<TasksRepository>().addTask(t1)
            },
        ) {
            awaitText("1-5 tabs")
            get<TaskManager>().startTask("t1")
            awaitText("1 running")
            val job = get<TasksRepository>().getTask("t1")!!.job!!

            val waited = measureTime { get<TaskManager>().stopAllTasks(timeout = 300.milliseconds) }

            try {
                assertTrue(waited < 5.seconds, waited.toString())
                assertFalse(job.isCompleted)
            } finally {
                StubbornScript.released = true
                job.join()
            }
        }
    }

    /** Swallows the interrupt a cancellation sends, so it runs until [released]. */
    class StubbornScript : Script<ScriptConfiguration> {
        override suspend fun onStart(
            configuration: ScriptConfiguration,
            provider: ComponentProvider,
        ) = true

        override suspend fun execute(
            configuration: ScriptConfiguration,
            provider: ComponentProvider,
            statusUpdate: suspend (message: String) -> Unit,
        ): ExecutionResult {
            while (!released) {
                try {
                    Thread.sleep(10)
                } catch (_: InterruptedException) {
                }
            }
            return ExecutionResult.Success("done")
        }

        override suspend fun onFinish(
            configuration: ScriptConfiguration,
            provider: ComponentProvider,
        ) = Unit

        companion object {
            @Volatile
            var released = false
        }
    }
}

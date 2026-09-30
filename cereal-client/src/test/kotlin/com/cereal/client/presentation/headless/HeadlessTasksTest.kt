package com.cereal.client.presentation.headless

import com.cereal.client.domain.model.ScopeLinker
import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.logging.LoggingPriority
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.domain.repository.LogEventRepository
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLogEventRepository
import com.cereal.sdk.ExecutionResult
import com.cereal.sdk.Script
import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.component.ComponentProvider
import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Keys
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import testutil.HeadlessTestScope
import testutil.runHeadlessTest
import java.io.File
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class HeadlessTasksTest {
    /** Runs until stopped. */
    class RunForeverScript : Script<ScriptConfiguration> {
        override suspend fun onStart(
            configuration: ScriptConfiguration,
            provider: ComponentProvider,
        ) = true

        override suspend fun execute(
            configuration: ScriptConfiguration,
            provider: ComponentProvider,
            statusUpdate: suspend (message: String) -> Unit,
        ): ExecutionResult = awaitCancellation()

        override suspend fun onFinish(
            configuration: ScriptConfiguration,
            provider: ComponentProvider,
        ) = Unit
    }

    private val alpha = ScriptPackageGroup("g-alpha", "Alpha")
    private val beta = ScriptPackageGroup("g-beta", "Beta")
    private val monitor = packageInstance("pkg-monitor", "Monitor")
    private val monitorScript = mainScript(monitor)

    private fun packageInstance(
        id: String,
        name: String,
    ): ScriptPackageInstance {
        val definition =
            ScriptPackage(
                source = File("."),
                manifest = Manifest(packageName = "com.example.$id", name = name, versionCode = 1),
                mainScript = MainScript(RunForeverScript::class, ScriptConfigurationDefinition(ScriptConfiguration::class, emptyList())),
                childScripts = emptyMap(),
            )
        return ScriptPackageInstance(id, emptyMap(), emptyMap(), definition, Clock.System.now(), numberOfConcurrentTasks = 1)
    }

    private fun mainScript(pkg: ScriptPackageInstance) = MainScriptInstance("script-${pkg.id}", pkg.definition.mainScript, emptyMap(), pkg.createdAt, pkg)

    private fun task(
        id: String,
        createdAt: Instant,
        status: TaskStatus = TaskStatus.Idle(timestamp = createdAt),
        interaction: UserInteraction? = null,
    ) = JobTask(id, monitorScript, emptyMap(), listOf(status), interaction, createdAt)

    private val t0 = Instant.fromEpochSeconds(1_700_000_000)

    /**
     * Alpha holds Monitor with [tasks]; Beta is empty. Monitor and its tasks are added once the tabs
     * are up, so the boot's task restore doesn't touch them.
     */
    private fun tasksTest(
        vararg tasks: JobTask,
        block: suspend HeadlessTestScope.() -> Unit,
    ) = runHeadlessTest(
        seed = {
            get<TasksRepository>().createScriptInstanceGroup(alpha)
            get<TasksRepository>().createScriptInstanceGroup(beta)
        },
    ) {
        awaitText("1-5 tabs")
        val linker = get<ScopeLinker>()
        linker.linkScriptInstanceToPackage(monitorScript)
        get<ScriptInstanceRepository>().addScriptPackageInstance(alpha.id, monitor, monitorScript)
        tasks.forEach(linker::linkTaskToScriptInstance)
        get<TasksRepository>().addAllTasks(tasks.toList())
        awaitText("Monitor")
        block()
    }

    private suspend fun HeadlessTestScope.status(taskId: String) = get<TasksRepository>().getTask(taskId)!!.status

    private suspend fun HeadlessTestScope.awaitStatus(
        taskId: String,
        done: (TaskStatus) -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + 2.seconds.inWholeMilliseconds
        while (!done(status(taskId))) {
            check(System.currentTimeMillis() < deadline) { "status of $taskId stayed ${status(taskId).message}" }
            kotlinx.coroutines.delay(10)
        }
    }

    /** Keys right behind an escape sequence can split it, so wait for the menu first. */
    private suspend fun HeadlessTestScope.openMenu() {
        press(CharKey('m'))
        awaitText("› Add group")
    }

    /** Rows: Alpha, Monitor, #1, #2…, Beta. */
    private suspend fun HeadlessTestScope.selectRow(index: Int) {
        repeat(index) { press(Keys.Down) }
    }

    @Test
    fun `the tree shows groups, scripts and tasks with running counts and a waiting flag`() =
        tasksTest(
            task("t1", t0, TaskStatus.Running("Checking stock", t0), UserInteraction.Browser("Captcha")),
            task("t2", t0 + 1.seconds),
        ) {
            val screen = awaitText("[! BROWSER]")
            val body = screen.joinToString("\n")
            assertTrue(screen.any { it.startsWith("› Alpha  1/2 running") }, body)
            assertTrue(screen.any { it.startsWith("    Monitor  1/2 running") }, body)
            assertTrue(screen.any { "● #1  Running  Checking stock  [! BROWSER]" in it }, body)
            assertTrue(screen.any { "○ #2  Idle" in it }, body)
            assertTrue(screen.any { it.startsWith("  Beta  0/0 running") }, body)
            assertTrue(screen.any { it.startsWith("  Default  0/0 running") }, body)
            assertTrue(screen[22].contains("s/x start/stop"), body)
        }

    @Test
    fun `s and x start and stop the selected task and persist its status`() =
        tasksTest(task("t1", t0)) {
            selectRow(2)
            press(CharKey('s'))
            awaitText("● #1  Running")
            awaitStatus("t1") { it is TaskStatus.Running }

            press(CharKey('x'))
            awaitText("○ #1  Idle")
            awaitStatus("t1") { it is TaskStatus.Idle }
        }

    @Test
    fun `S and X start and stop the tasks of the selected script`() =
        tasksTest(task("t1", t0), task("t2", t0 + 1.seconds)) {
            selectRow(1)
            press(CharKey('S'))
            awaitText("Monitor  1/2 running")
            awaitStatus("t1") { it is TaskStatus.Running }

            press(CharKey('X'))
            awaitText("Monitor  0/2 running")
            awaitStatus("t1") { it is TaskStatus.Idle }
        }

    @Test
    fun `restarting a finished task asks for confirmation`() =
        tasksTest(task("t1", t0, TaskStatus.Success("Checked out", t0))) {
            selectRow(2)
            press(CharKey('s'))
            awaitText("Restart finished task #1? [y/N]")
            press(CharKey('n'))
            awaitScreen { lines -> lines.none { "Restart finished" in it } }
            assertTrue(status("t1") is TaskStatus.Success)

            press(CharKey('s'))
            awaitText("Restart finished task #1? [y/N]")
            press(CharKey('y'))
            awaitStatus("t1") { it is TaskStatus.Running }
        }

    @Test
    fun `task detail shows filtered logs and expands an error's stack trace`() =
        tasksTest(task("t1", t0, TaskStatus.Error("Payment declined", "java.lang.IllegalStateException\n\tat Checkout.pay", t0 + 3.seconds))) {
            (get<LogEventRepository>() as InMemoryLogEventRepository).seed(
                "t1",
                listOf(
                    LoggingEvent(LoggingPriority.INFO, "t1", "Opened product page", Date(t0.toEpochMilliseconds() + 1000)),
                    LoggingEvent(LoggingPriority.WARNING, "t1", "Slow response", Date(t0.toEpochMilliseconds() + 2000)),
                ),
            )
            selectRow(2)
            press(Keys.Enter)
            var screen = awaitText("Opened product page")
            assertTrue(screen.any { "Monitor · task #1 · Error" in it }, screen.joinToString("\n"))
            assertTrue(screen.any { "INFO Opened product page" in it })
            assertTrue(screen.any { "WARN Slow response" in it })
            assertTrue(screen.any { "ERR  Payment declined" in it })
            assertTrue(screen.any { "(t shows the stack trace)" in it })

            press(CharKey('f'), CharKey('f'))
            screen = awaitText("Logs · WARN")
            assertTrue(screen.none { "Opened product page" in it })
            assertTrue(screen.any { "Slow response" in it })

            press(CharKey('f'))
            screen = awaitText("Logs · ERR")
            assertTrue(screen.none { "Slow response" in it })

            press(CharKey('t'))
            awaitText("at Checkout.pay")

            press(Keys.Escape)
            awaitText("Beta  0/0 running")
        }

    @Test
    fun `the action menu adds, renames and deletes a group`() =
        tasksTest {
            awaitText("Beta  0/0 running")
            press(CharKey('m'))
            awaitText("› Add group")
            press(Keys.Enter)
            awaitText("New group name: _")
            type("Gamma")
            press(Keys.Enter)
            awaitText("Gamma  0/0 running")

            // Rows: Alpha, Monitor, Beta, Default, Gamma.
            repeat(4) { press(Keys.Down) }
            openMenu()
            press(Keys.Down, Keys.Enter)
            awaitText("Rename group: Gamma_")
            // One key at a time: keys pressed back to back can split an escape sequence in the test terminal.
            for (left in 4 downTo 0) {
                press(Keys.Backspace)
                awaitText("Rename group: ${"Gamma".take(left)}_")
            }
            type("Delta")
            press(Keys.Enter)
            awaitText("Delta  0/0 running")
            assertTrue(get<TasksRepository>().getTaskGroups().first().any { it.name == "Delta" })

            openMenu()
            press(Keys.Down, Keys.Down, Keys.Enter)
            awaitText("Delete group Delta and all its scripts? [y/N]")
            press(CharKey('y'))
            awaitScreen { lines -> lines.none { "Delta" in it } }
        }

    @Test
    fun `the action menu moves a script to another group and deletes it`() =
        tasksTest(task("t1", t0)) {
            selectRow(1)
            press(CharKey('m'))
            awaitText("› Add group")
            press(Keys.Down, Keys.Enter)
            awaitText("Move Monitor to")
            // Targets: Beta, Default.
            press(Keys.Enter)
            val screen = awaitScreen { lines -> lines.indexOfFirst { it.contains("Beta  0/1") } in 0 until lines.indexOfFirst { "Monitor" in it } }
            assertTrue(screen.any { it.contains("Alpha  0/0 running") }, screen.joinToString("\n"))

            openMenu()
            press(Keys.Down, Keys.Down, Keys.Enter)
            awaitText("Delete Monitor and its tasks? [y/N]")
            press(CharKey('y'))
            awaitScreen { lines -> lines.none { "Monitor" in it } }
            assertEquals(null, get<TasksRepository>().getTask("t1"))
        }
}

package com.cereal.client.presentation.headless

import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.ScopeLinker
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.provider.SessionLostProvider
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.SessionRepository
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.client.infrastructure.provider.SessionLostProviderImpl
import com.cereal.sdk.ScriptConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import testutil.HeadlessTestScope
import testutil.runHeadlessTest
import java.io.File
import java.util.Collections
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class HeadlessSessionLostTest {
    private val group = ScriptPackageGroup("g-alpha", "Alpha")
    private val pkg =
        ScriptPackageInstance(
            "pkg-monitor",
            emptyMap(),
            emptyMap(),
            ScriptPackage(
                source = File("."),
                manifest = Manifest(packageName = "com.example.monitor", name = "Monitor", versionCode = 1),
                mainScript =
                    MainScript(
                        HeadlessTasksTest.RunForeverScript::class,
                        ScriptConfigurationDefinition(ScriptConfiguration::class, emptyList()),
                    ),
                childScripts = emptyMap(),
            ),
            Clock.System.now(),
            numberOfConcurrentTasks = 1,
        )
    private val script = MainScriptInstance("script-monitor", pkg.definition.mainScript, emptyMap(), pkg.createdAt, pkg)
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)
    private val task = JobTask("t1", script, emptyMap(), listOf(TaskStatus.Idle(timestamp = t0)), null, t0)

    private fun HeadlessTestScope.reportSessionLost() = (get<SessionLostProvider>() as SessionLostProviderImpl).report()

    @Test
    fun `a session lost while a task runs stops it as Idle and shows login with the reason`() =
        runHeadlessTest(seed = { get<TasksRepository>().createScriptInstanceGroup(group) }) {
            awaitText("1-5 tabs")
            val linker = get<ScopeLinker>()
            linker.linkScriptInstanceToPackage(script)
            get<ScriptInstanceRepository>().addScriptPackageInstance(group.id, pkg, script)
            linker.linkTaskToScriptInstance(task)
            get<TasksRepository>().addTask(task)
            // The in-memory repository forgets tasks on sign-out, so record the statuses it persists.
            val statuses = Collections.synchronizedList(mutableListOf<TaskStatus>())
            val recorder = CoroutineScope(Dispatchers.Default)
            recorder.launch {
                get<TasksRepository>().getAllTasks().collect { tasks -> tasks.find { it.id == "t1" }?.let { statuses += it.status } }
            }
            get<TaskManager>().startTask("t1")
            awaitText("1 running")

            reportSessionLost()

            awaitText(HeadlessTui.SESSION_LOST)
            awaitText("Sign in to Cereal")
            recorder.cancel()
            assertEquals(TaskStatus.Idle::class, statuses.last()::class, statuses.toString())
            assertTrue(statuses.any { it is TaskStatus.Running }, statuses.toString())
            assertNull(get<SessionRepository>().getAuthenticatedUserFlow().first())
        }

    @Test
    fun `a session lost while signed out is ignored`() =
        runHeadlessTest(seed = { get<SessionRepository>().setSessionUser(null) }) {
            awaitText("Sign in to Cereal")
            reportSessionLost()
            awaitText("Sign in to Cereal")
            delay(200)
            assertTrue(screen().none { HeadlessTui.SESSION_LOST in it })
        }
}

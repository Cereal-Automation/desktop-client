package com.cereal.client.infrastructure.di.modules

import com.cereal.client.application.interactor.script.StartScriptInteractor
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.diagnostics.FailureReport
import com.cereal.client.domain.model.diagnostics.RunTrace
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.url.WebUrl
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.domain.provider.FailureReportProvider
import com.cereal.client.domain.repository.LlmProviderConfigRepository
import com.cereal.client.domain.repository.RunTraceRepository
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.ScriptPreferenceRepository
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.domain.repository.SessionRepository
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.client.infrastructure.di.Injector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.time.Clock

/**
 * The `mock` flavor keeps the LLM config and everything under Tasks across restarts: the sandbox graph is
 * started, used and stopped, then started again on the same sandbox directory.
 *
 * Uses `runBlocking` rather than `runTest`: the real Room database runs on real IO dispatchers.
 */
class SandboxPersistenceTest {
    @TempDir
    lateinit var sandboxDir: File

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `llm config, groups, task setups, script preferences and recent checks survive a restart`() =
        runBlocking {
            val llmConfig =
                LlmProviderConfig(
                    provider = LlmProviderType.ANTHROPIC,
                    baseUrl = WebUrl.of(LlmProviderType.ANTHROPIC.defaultBaseUrl),
                    apiKey = Secret("sk-test"),
                    model = "claude-test",
                )

            val (taskSetupId, runningTaskId) =
                withSandbox { koin ->
                    koin.get<LlmProviderConfigRepository>().save(llmConfig)
                    val group = koin.get<TasksRepository>().createScriptInstanceGroup(ScriptPackageGroup(GROUP_ID, "Drops"))
                    val scriptPackage = koin.get<ScriptRepository>().getScript(PACKAGE_NAME)!!
                    val taskSetup =
                        koin.get<StartScriptInteractor>().run(
                            StartScriptInteractor.Params(
                                groupId = group.id,
                                scriptPackage = scriptPackage,
                                mainScriptConfiguration = mapOf("goal" to ConfigValue.StringValue("buy shoes")),
                                childConfigurations = emptyMap(),
                                numberOfConcurrentTasks = 2,
                                ignoreConcurrencyConflicts = true,
                            ),
                        )
                    koin.get<ScriptPreferenceRepository>().setString(taskSetup.id, "cursor", "42")

                    // Leave one task running when the app "closes".
                    val tasks = koin.get<TasksRepository>()
                    val task = tasks.getTasks(taskSetup).first()
                    tasks.createPersistedTask(task.id)
                    tasks.addStatusHistory(task.id, TaskStatus.Running(timestamp = Clock.System.now()))

                    koin.get<RunTraceRepository>().add(runTrace(task.id), TRACE_BYTES)
                    koin.get<FailureReportProvider>().upload(
                        FailureReport("run-1", "check-1", "flagged", "wrong price", TRACE_BYTES, null),
                    ) { true }
                    taskSetup.id to task.id
                }

            withSandbox { koin ->
                assertEquals(llmConfig, koin.get<LlmProviderConfigRepository>().get())

                val groups = koin.get<TasksRepository>().getTaskGroups().first()
                assertEquals(listOf("Drops"), groups.filter { it.id == GROUP_ID }.map { it.name })

                val taskSetup = koin.get<ScriptInstanceRepository>().getScriptPackageInstance(taskSetupId)
                assertEquals(ConfigValue.StringValue("buy shoes"), taskSetup.mainConfiguration["goal"])
                assertEquals(2, taskSetup.numberOfConcurrentTasks)
                assertEquals(1, koin.get<ScriptInstanceRepository>().getScriptPackageInstances().size)

                assertEquals("42", koin.get<ScriptPreferenceRepository>().getString(taskSetupId, "cursor").first())

                koin.get<TaskManager>().restoreTasks(taskSetup)
                val restored = koin.get<TasksRepository>().getTasks(taskSetup)
                assertTrue(restored.single { it.id == runningTaskId }.status is TaskStatus.Idle)
                assertFalse(restored.any { it.status is TaskStatus.Running })

                // Recent checks survive, with their bytes readable as plain files for debugging.
                val traces = koin.get<RunTraceRepository>()
                val trace = traces.getAll().single()
                assertEquals(runningTaskId, trace.taskId)
                assertArrayEquals(TRACE_BYTES, traces.readBytes(trace))
                assertTrue(sandboxDir.resolve("diagnostics").walk().any { it.isFile && it.readBytes().contentEquals(TRACE_BYTES) })

                // A reported check is written to disk instead of uploaded.
                val report = sandboxDir.resolve("failure-reports").listFiles()!!.single()
                assertTrue(report.resolve("report.txt").readText().contains("note: wrong price"))
                assertArrayEquals(TRACE_BYTES, report.resolve("trace").readBytes())
            }
        }

    @Test
    fun `a cleared llm config stays cleared after a restart`() =
        runBlocking {
            withSandbox { koin ->
                val repository = koin.get<LlmProviderConfigRepository>()
                repository.save(
                    LlmProviderConfig(
                        provider = LlmProviderType.ANTHROPIC,
                        baseUrl = WebUrl.of(LlmProviderType.ANTHROPIC.defaultBaseUrl),
                        apiKey = Secret("sk-test"),
                        model = "claude-test",
                    ),
                )
                repository.clear()
            }

            withSandbox { koin -> assertNull(koin.get<LlmProviderConfigRepository>().get()) }
        }

    /** Starts the mock-flavor graph on [sandboxDir], signs in the sandbox user, runs [block] and stops the graph. */
    private suspend fun <T> withSandbox(block: suspend (Koin) -> T): T {
        val koin =
            startKoin {
                modules(
                    Injector.appModules(InMemoryRepositoryModule.modules, InMemoryProviderModule.modules) +
                        SandboxRepositoryModule.modules(sandboxDir),
                )
            }.koin
        try {
            val user = koin.get<AuthProvider>().authenticateGuest()
            writeScriptJar(File(sandboxDir, "scripts/${user.id}"))
            koin.get<SessionRepository>().setSessionUser(user)
            return block(koin)
        } finally {
            koin.get<SessionRepository>().setSessionUser(null)
            stopKoin()
        }
    }

    /** An unencrypted, manifest-only script jar; its script class comes from the test classpath. */
    private fun writeScriptJar(userScriptsDir: File) {
        userScriptsDir.mkdirs()
        val manifest =
            """
            { "package_name": "$PACKAGE_NAME", "name": "Persist", "version_code": 1,
              "script": "fixtures.scripts.DefaultEntryScript" }
            """.trimIndent()
        val bytes =
            ByteArrayOutputStream()
                .also { out ->
                    JarOutputStream(out).use { jar ->
                        jar.putNextEntry(JarEntry("manifest.json"))
                        jar.write(manifest.toByteArray())
                        jar.closeEntry()
                    }
                }.toByteArray()
        File(userScriptsDir, "persist.jar").writeBytes(bytes)
    }

    private fun runTrace(taskId: String) =
        RunTrace(
            id = "trace-1",
            taskId = taskId,
            key = "check-1",
            runId = "run-1",
            outcome = "ok",
            label = "Price check",
            notable = false,
            learned = false,
            sizeBytes = TRACE_BYTES.size.toLong(),
            receivedAt = Clock.System.now(),
        )

    private companion object {
        val TRACE_BYTES = "{\"steps\":[]}".toByteArray()
        const val PACKAGE_NAME = "com.example.persist"
        const val GROUP_ID = "6f1c2a7e-3b0d-4c1e-9a55-2d8f0e4b7c11"
    }
}

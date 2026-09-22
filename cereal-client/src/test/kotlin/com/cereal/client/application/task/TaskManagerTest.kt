package com.cereal.client.application.task

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import com.cereal.sdk.ScriptConfiguration
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class TaskManagerTest {
    private val scriptInstanceRepository = mockk<ScriptInstanceRepository>(relaxed = true)
    private val taskConfigurationBuilder = mockk<TaskConfigurationBuilder>(relaxed = true)

    private val configDef =
        ScriptConfigurationDefinition(
            scriptConfigurationClass = ScriptConfiguration::class,
            configurationItems = emptyList(),
        )

    private val scriptPackage =
        ScriptPackage(
            source = File("."),
            manifest = mockk(relaxed = true),
            mainScript = MainScript(clazz = com.cereal.sdk.Script::class, configuration = configDef),
            childScripts = emptyMap(),
        )

    private val packageInstance =
        ScriptPackageInstance(
            id = "pkg-1",
            mainConfiguration = emptyMap(),
            childConfigurations = emptyMap(),
            definition = scriptPackage,
            createdAt = Clock.System.now(),
            numberOfConcurrentTasks = 2,
        )

    private val scriptInstance =
        MainScriptInstance(
            id = "script-1",
            definition = scriptPackage.mainScript,
            configuration = emptyMap(),
            createdAt = Clock.System.now(),
            packageInstance = packageInstance,
        )

    private fun buildTask(
        id: String,
        statusHistory: List<TaskStatus> = emptyList(),
    ) = JobTask(
        id = id,
        scriptInstance = scriptInstance,
        configuration = emptyMap(),
        statusHistory = statusHistory,
        createdAt = Clock.System.now(),
    )

    /**
     * Regression test for the "Task already running" race condition introduced in 1.9.0.
     *
     * Before the fix: two concurrent callers both captured the stale JobTask snapshot (job == null),
     * both passed performPreStartCheck, and both launched a TaskExecutor — resulting in duplicate
     * execution. The second caller would eventually throw from TaskExecutor internals.
     *
     * After the fix: startTask re-fetches from the repository inside startTaskMutex, so the second
     * caller sees the Running status written by the first and stops via MaxConcurrentTasksReachedException
     * rather than proceeding to launch a duplicate executor.
     *
     * This test verifies that two sequential calls (which simulate the post-mutex serialisation that
     * the fix provides) do not both reach the point of writing a Running status entry. The first call
     * writes Running; the second call must be rejected before writing its own Running entry.
     */
    @Test
    fun `startTask second call should not add a second Running status when first call already set Running`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            coEvery { taskConfigurationBuilder.get(any()) } returns emptyList()

            val taskManager =
                TaskManager(
                    dispatcherProvider = CoroutinesDispatcherProvider(),
                    tasksRepository = tasksRepository,
                    taskConfigurationBuilder = taskConfigurationBuilder,
                    scriptInstanceRepository = scriptInstanceRepository,
                    jobTaskFactory = mockk(relaxed = true),
                    artifactRepository = mockk(relaxed = true),
                )

            val task = buildTask("task-race")
            tasksRepository.addTask(task)

            // First call should succeed and mark the task as Running.
            runCatching { taskManager.startTask("task-race") }

            val runningCountAfterFirst =
                tasksRepository
                    .getTask("task-race")
                    ?.statusHistory
                    ?.count { it is TaskStatus.Running }
                    ?: 0

            // Second call should be rejected (MaxConcurrentTasksReachedException or
            // IllegalStateException) — but crucially must NOT add another Running entry.
            runCatching { taskManager.startTask("task-race") }

            val runningCountAfterSecond =
                tasksRepository
                    .getTask("task-race")
                    ?.statusHistory
                    ?.count { it is TaskStatus.Running }
                    ?: 0

            assertTrue(runningCountAfterFirst >= 1) {
                "Expected first startTask call to add a Running status, but statusHistory was empty"
            }
            assertEquals(runningCountAfterFirst, runningCountAfterSecond) {
                "Second startTask call must not add another Running status entry. " +
                    "Before: $runningCountAfterFirst Running entries, after: $runningCountAfterSecond Running entries"
            }
        }

    /**
     * A task that was still Running when the application closed is a "zombie" on the next launch.
     * Being interrupted by an app shutdown is not a failure, so restoreTasks must return it to Idle
     * rather than marking it as an Error.
     */
    @Test
    fun `restoreTasks moves a task left Running at shutdown back to Idle, not Error`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            coEvery { taskConfigurationBuilder.get(any()) } returns emptyList()
            coEvery { scriptInstanceRepository.getScriptInstances(packageInstance) } returns listOf(scriptInstance)

            val taskManager =
                TaskManager(
                    dispatcherProvider = CoroutinesDispatcherProvider(),
                    tasksRepository = tasksRepository,
                    taskConfigurationBuilder = taskConfigurationBuilder,
                    scriptInstanceRepository = scriptInstanceRepository,
                    jobTaskFactory = mockk(relaxed = true),
                    artifactRepository = mockk(relaxed = true),
                )

            // Simulate a task that was persisted in the Running state when the app was closed.
            tasksRepository.addTask(
                buildTask("zombie", statusHistory = listOf(TaskStatus.Running("Working", Clock.System.now()))),
            )

            taskManager.restoreTasks(packageInstance)

            val restoredStatus = tasksRepository.getTask("zombie")?.status
            assertTrue(restoredStatus is TaskStatus.Idle) {
                "Expected an interrupted task to be restored as Idle, but was $restoredStatus"
            }
        }
}

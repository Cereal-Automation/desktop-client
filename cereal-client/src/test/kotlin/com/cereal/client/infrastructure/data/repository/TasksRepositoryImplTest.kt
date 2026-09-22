package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.user.User
import com.cereal.client.fixtures.InMemoryScriptInstanceDataSource
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class TasksRepositoryImplTest {
    private lateinit var repository: TasksRepositoryImpl

    private val scriptInstanceDataSource = InMemoryScriptInstanceDataSource()
    private val scriptInstanceRepository = InMemoryScriptInstanceRepository()
    private val userSession = mockk<UserSession>(relaxed = true)
    private val user = User("u", "n", "e", "k", "t", false)

    @BeforeEach
    fun setUp() {
        // removeTask resolves a Koin scope by task id, so a running Koin app is required.
        startKoin { }
        repository =
            TasksRepositoryImpl(
                scriptInstanceDataSource = scriptInstanceDataSource,
                scriptInstanceRepository = scriptInstanceRepository,
                userSession = userSession,
            )
        // MockK: UserSession is a precondition (auth state), not the seam under test.
        coEvery { userSession.requireUser() } returns user
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    private fun createTask(
        id: String,
        scriptInstanceId: String = "instance-$id",
    ): JobTask {
        val scriptInstance = mockk<ScriptInstance>(relaxed = true)
        every { scriptInstance.id } returns scriptInstanceId
        return JobTask(
            id = id,
            scriptInstance = scriptInstance,
            configuration = mockk(relaxed = true),
            statusHistory = emptyList(),
            createdAt = Instant.fromEpochMilliseconds(0),
        )
    }

    @Test
    fun `addStatusHistory should cap status history to MAX_STATUS_HISTORY_SIZE entries`() =
        runTest {
            // Given
            val task = createTask("task-1")
            repository.addTask(task)

            // When - add more entries than the cap
            val entryCount = JobTask.MAX_STATUS_HISTORY_SIZE + 50
            repeat(entryCount) { i ->
                repository.addStatusHistory(task.id, TaskStatus.Running("status-$i", Clock.System.now()))
            }

            // Then - history should be capped
            val storedTask = repository.getTask(task.id)!!
            assertTrue(
                storedTask.statusHistory.size <= JobTask.MAX_STATUS_HISTORY_SIZE,
                "Expected statusHistory to be at most ${JobTask.MAX_STATUS_HISTORY_SIZE} " +
                    "but was ${storedTask.statusHistory.size}",
            )
        }

    @Test
    fun `addStatusHistory should retain the most recent entries when capping`() =
        runTest {
            // Given
            val task = createTask("task-1")
            repository.addTask(task)

            // When - add more entries than the cap
            val entryCount = JobTask.MAX_STATUS_HISTORY_SIZE + 10
            repeat(entryCount) { i ->
                repository.addStatusHistory(task.id, TaskStatus.Running("status-$i", Clock.System.now()))
            }

            // Then - the last entry should be the most recent one
            val storedTask = repository.getTask(task.id)!!
            val lastEntry = storedTask.statusHistory.last() as TaskStatus.Running
            assertEquals("status-${entryCount - 1}", lastEntry.message)
        }

    @Test
    fun `addStatusHistory should not truncate history below the cap`() =
        runTest {
            // Given
            val task = createTask("task-1")
            repository.addTask(task)

            // When - add fewer entries than the cap
            val entryCount = 5
            repeat(entryCount) { i ->
                repository.addStatusHistory(task.id, TaskStatus.Running("status-$i", Clock.System.now()))
            }

            // Then - all entries should be retained
            val storedTask = repository.getTask(task.id)!!
            assertEquals(entryCount, storedTask.statusHistory.size)
        }

    @Test
    fun `addTask stores the task and emits it on the flow`() =
        runTest {
            val task = createTask("task-1")

            repository.addTask(task)

            assertEquals(task, repository.getTask("task-1"))
            assertEquals(listOf(task), repository.getAllTasks().first())
        }

    @Test
    fun `addAllTasks stores all tasks`() =
        runTest {
            val taskA = createTask("a")
            val taskB = createTask("b")

            repository.addAllTasks(listOf(taskA, taskB))

            assertEquals(taskA, repository.getTask("a"))
            assertEquals(taskB, repository.getTask("b"))
            assertEquals(2, repository.getAllTasks().first().size)
        }

    @Test
    fun `getTask returns null when the task is unknown`() =
        runTest {
            assertNull(repository.getTask("missing"))
        }

    @Test
    fun `removeTask removes the task from the store`() =
        runTest {
            val task = createTask("task-1")
            repository.addTask(task)

            repository.removeTask(task)

            assertNull(repository.getTask("task-1"))
            assertEquals(emptyList<JobTask>(), repository.getAllTasks().first())
        }

    @Test
    fun `removeAllTasks clears the store`() =
        runTest {
            repository.addTask(createTask("a"))
            repository.addTask(createTask("b"))

            repository.removeAllTasks()

            assertEquals(emptyList<JobTask>(), repository.getAllTasks().first())
        }

    @Test
    fun `setTaskJob updates the stored task job`() =
        runTest {
            val task = createTask("task-1")
            repository.addTask(task)
            val job = mockk<kotlinx.coroutines.Job>(relaxed = true)

            repository.setTaskJob("task-1", job)

            assertEquals(job, repository.getTask("task-1")?.job)
        }

    @Test
    fun `setTaskJob is a no-op when the task does not exist`() =
        runTest {
            repository.setTaskJob("missing", mockk(relaxed = true))

            assertNull(repository.getTask("missing"))
        }

    @Test
    fun `setUserInteraction updates the stored task interaction`() =
        runTest {
            val task = createTask("task-1")
            repository.addTask(task)
            val interaction = mockk<com.cereal.client.domain.model.task.UserInteraction>(relaxed = true)

            repository.setUserInteraction("task-1", interaction)

            assertEquals(interaction, repository.getTask("task-1")?.userInteraction)
        }

    @Test
    fun `setUserInteraction is a no-op when the task does not exist`() =
        runTest {
            repository.setUserInteraction("missing", mockk(relaxed = true))

            assertNull(repository.getTask("missing"))
        }

    @Test
    fun `getTasks by script instance returns only matching tasks`() =
        runTest {
            val matchingA = createTask("a", scriptInstanceId = "si-1")
            val matchingB = createTask("b", scriptInstanceId = "si-1")
            val other = createTask("c", scriptInstanceId = "si-2")
            repository.addAllTasks(listOf(matchingA, matchingB, other))

            val target = mockk<ScriptInstance>(relaxed = true)
            every { target.id } returns "si-1"

            val result = repository.getTasks(target)

            assertEquals(2, result.size)
            assertTrue(result.all { it.scriptInstance.id == "si-1" })
        }

    @Test
    fun `getTasks by package instance is empty when the instance has no script instances`() =
        runTest {
            // The in-memory ScriptInstanceRepository returns no script instances for a package
            // instance, so the aggregation across instances yields nothing.
            repository.addAllTasks(listOf(createTask("a"), createTask("b")))
            val packageInstance = mockk<com.cereal.client.domain.model.script.ScriptPackageInstance>(relaxed = true)

            val result = repository.getTasks(packageInstance)

            assertTrue(result.isEmpty())
        }

    @Test
    fun `getTasksFlow is empty when the package instance has no script instances`() =
        runTest {
            repository.addAllTasks(listOf(createTask("a"), createTask("b")))
            val packageInstance = mockk<com.cereal.client.domain.model.script.ScriptPackageInstance>(relaxed = true)

            val result = repository.getTasksFlow(packageInstance).first()

            assertTrue(result.isEmpty())
        }

    @Test
    fun `createPersistedTask persists the stored task to history`() =
        runTest {
            val scriptInstance = mockk<ScriptInstance>(relaxed = true)
            every { scriptInstance.id } returns "si-1"
            val task =
                JobTask(
                    id = "task-1",
                    scriptInstance = scriptInstance,
                    configuration = mockk(relaxed = true),
                    statusHistory = emptyList(),
                    createdAt = Instant.fromEpochMilliseconds(0),
                )
            repository.addTask(task)

            repository.createPersistedTask("task-1")

            val history = repository.getJobTasksFromHistory(scriptInstance)
            assertEquals(listOf("task-1"), history.map { it.id })
        }

    @Test
    fun `createPersistedTask is a no-op when the task does not exist`() =
        runTest {
            val scriptInstance = mockk<ScriptInstance>(relaxed = true)
            every { scriptInstance.id } returns "si-1"

            repository.createPersistedTask("missing")

            assertTrue(repository.getJobTasksFromHistory(scriptInstance).isEmpty())
        }

    @Test
    fun `deletePersistedTask removes the task from history`() =
        runTest {
            val scriptInstance = mockk<ScriptInstance>(relaxed = true)
            every { scriptInstance.id } returns "si-1"
            val task =
                JobTask(
                    id = "task-1",
                    scriptInstance = scriptInstance,
                    configuration = mockk(relaxed = true),
                    statusHistory = emptyList(),
                    createdAt = Instant.fromEpochMilliseconds(0),
                )
            repository.addTask(task)
            repository.createPersistedTask("task-1")

            repository.deletePersistedTask("task-1")

            assertTrue(repository.getJobTasksFromHistory(scriptInstance).isEmpty())
        }

    @Test
    fun `createScriptInstanceGroup persists and returns the group`() =
        runTest {
            val group = ScriptPackageGroup(id = "g1", name = "Group")

            val result = repository.createScriptInstanceGroup(group)

            assertEquals(group, result)
            assertEquals(listOf(group), repository.getTaskGroups().first())
        }

    @Test
    fun `updateScriptInstanceGroup updates the stored group`() =
        runTest {
            val group = ScriptPackageGroup(id = "g1", name = "Group")
            repository.createScriptInstanceGroup(group)

            repository.updateScriptInstanceGroup(group.copy(name = "Renamed"))

            assertEquals(
                "Renamed",
                repository
                    .getTaskGroups()
                    .first()
                    .single()
                    .name,
            )
        }

    @Test
    fun `deleteTaskGroup removes the group`() =
        runTest {
            val group = ScriptPackageGroup(id = "g1", name = "Group")
            repository.createScriptInstanceGroup(group)

            repository.deleteTaskGroup(group)

            assertTrue(repository.getTaskGroups().first().isEmpty())
        }

    @Test
    fun `getTaskGroups starts empty`() =
        runTest {
            assertTrue(repository.getTaskGroups().first().isEmpty())
        }

    @Test
    fun `getJobTasksFromHistory returns persisted tasks for the script instance`() =
        runTest {
            val scriptInstance = mockk<ScriptInstance>(relaxed = true)
            every { scriptInstance.id } returns "si-history"
            val task =
                JobTask(
                    id = "history-1",
                    scriptInstance = scriptInstance,
                    configuration = mockk(relaxed = true),
                    statusHistory = emptyList(),
                    createdAt = Instant.fromEpochMilliseconds(0),
                )
            repository.addTask(task)
            repository.createPersistedTask("history-1")

            val result = repository.getJobTasksFromHistory(scriptInstance)

            assertEquals(listOf("history-1"), result.map { it.id })
        }
}

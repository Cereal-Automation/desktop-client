package com.cereal.client.application.task

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.exception.StopTaskFirstException
import com.cereal.client.application.exception.TaskActionAlreadyRunningException
import com.cereal.client.application.exception.TaskActionInProgressException
import com.cereal.client.application.exception.TaskActionTimeoutException
import com.cereal.client.fixtures.FakeTaskActionRunner
import com.cereal.client.fixtures.mainTaskFixture
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class TaskManagerTaskActionTest {
    private val tasksRepository = InMemoryTasksRepository()
    private val runner = FakeTaskActionRunner()
    private val task = mainTaskFixture()
    private val instance = task.scriptInstance.packageInstance

    private fun TestScope.taskManager(): TaskManager {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return TaskManager(
            dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher),
            tasksRepository = tasksRepository,
            taskConfigurationBuilder = mockk(relaxed = true),
            scriptInstanceRepository = mockk(relaxed = true),
            jobTaskFactory = mockk(relaxed = true),
            artifactRepository = mockk(relaxed = true),
            taskActionRunner = runner,
        )
    }

    @Test
    fun `an action outlives the caller that started it and its result waits to be shown`() =
        runTest {
            tasksRepository.addTask(task)
            val manager = taskManager()
            val finish = CompletableDeferred<String?>()
            runner.onRun = { finish.await() }

            val callerScope = CoroutineScope(StandardTestDispatcher(testScheduler))
            callerScope.launch { manager.startTaskAction(instance, "Check balance") }
            testScheduler.runCurrent()
            // Leaving the Tasks screen cancels the screen's scope.
            callerScope.cancel()

            assertEquals(setOf(instance.id), manager.taskActions.value.inProgress)

            finish.complete("Balance: 5")
            testScheduler.advanceUntilIdle()

            assertTrue(
                manager.taskActions.value.inProgress
                    .isEmpty(),
            )
            val result =
                assertIs<TaskActionResult.Completed>(
                    manager.taskActions.value.results
                        .single(),
                )
            assertEquals("Balance: 5", result.message)
        }

    @Test
    fun `an exception thrown by the action surfaces with its own message`() =
        runTest {
            tasksRepository.addTask(task)
            val manager = taskManager()
            runner.onRun = { throw IllegalStateException("Login failed") }

            manager.startTaskAction(instance, "Check balance")
            testScheduler.advanceUntilIdle()

            val result =
                assertIs<TaskActionResult.Failed>(
                    manager.taskActions.value.results
                        .single(),
                )
            assertIs<CerealException>(result.error)
            assertEquals("Login failed", result.error.message)
        }

    @Test
    fun `an Error thrown by the action does not leave the instance blocked`() {
        // runTest rethrows the uncaught Error at the end; the assertions inside it run first.
        assertThrows<NotImplementedError> {
            runTest {
                tasksRepository.addTask(task)
                val manager = taskManager()
                runner.onRun = { TODO() }

                manager.startTaskAction(instance, "Check balance")
                testScheduler.advanceUntilIdle()

                assertTrue(
                    manager.taskActions.value.inProgress
                        .isEmpty(),
                )
            }
        }
    }

    @Test
    fun `an action that outlives the timeout fails with a timeout`() =
        runTest {
            tasksRepository.addTask(task)
            val manager = taskManager()
            runner.onRun = {
                delay(60_000)
                "too late"
            }

            manager.startTaskAction(instance, "Check balance")
            testScheduler.advanceTimeBy(14_000)
            assertEquals(setOf(instance.id), manager.taskActions.value.inProgress)

            testScheduler.advanceTimeBy(2_000)
            testScheduler.runCurrent()

            val result =
                assertIs<TaskActionResult.Failed>(
                    manager.taskActions.value.results
                        .single(),
                )
            assertIs<TaskActionTimeoutException>(result.error)
            assertTrue(
                manager.taskActions.value.inProgress
                    .isEmpty(),
            )
        }

    @Test
    fun `the task cannot start while an action runs`() =
        runTest {
            tasksRepository.addTask(task)
            val manager = taskManager()
            runner.onRun = { CompletableDeferred<String?>().await() }

            manager.startTaskAction(instance, "Check balance")
            testScheduler.runCurrent()

            assertThrows<TaskActionInProgressException> { manager.startTask(task.id) }
        }

    @Test
    fun `an action cannot run while the task runs`() =
        runTest {
            tasksRepository.addTask(
                task.copy(
                    statusHistory =
                        listOf(
                            com.cereal.client.domain.model.task.TaskStatus
                                .Running("Working", Clock.System.now()),
                        ),
                ),
            )
            val manager = taskManager()

            assertThrows<StopTaskFirstException> { manager.startTaskAction(instance, "Check balance") }
            assertTrue(runner.runs.isEmpty())
        }

    @Test
    fun `a second action on the same instance is refused while the first runs`() =
        runTest {
            tasksRepository.addTask(task)
            val manager = taskManager()
            runner.onRun = { CompletableDeferred<String?>().await() }

            manager.startTaskAction(instance, "Check balance")
            testScheduler.runCurrent()

            assertThrows<TaskActionAlreadyRunningException> { manager.startTaskAction(instance, "Reset") }
        }

    @Test
    fun `a dismissed result is no longer pending`() =
        runTest {
            tasksRepository.addTask(task)
            val manager = taskManager()
            runner.onRun = { "ok" }

            manager.startTaskAction(instance, "Check balance")
            testScheduler.advanceUntilIdle()
            manager.dismissTaskActionResult(
                manager.taskActions.value.results
                    .single(),
            )

            assertTrue(
                manager.taskActions.value.results
                    .isEmpty(),
            )
        }
}

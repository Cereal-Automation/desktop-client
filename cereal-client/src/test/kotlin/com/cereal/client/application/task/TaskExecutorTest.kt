package com.cereal.client.application.task

import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.fixtures.mainTaskFixture
import com.cereal.sdk.ExecutionResult
import com.cereal.sdk.Script
import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.component.ComponentProvider
import com.cereal.sdk.component.llm.LlmException
import com.cereal.sdk.component.logger.LoggerComponent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertIs

class TaskExecutorTest {
    private val logger = mockk<LoggerComponent>(relaxed = true)
    private val componentProvider = mockk<ComponentProvider> { every { logger() } returns logger }

    @BeforeEach
    fun setUp() {
        mockkObject(CrashReporter)
        startKoin { modules(module { scope<Task> { scoped<ComponentProvider> { componentProvider } } }) }
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
        unmockkObject(CrashReporter)
    }

    @Test
    fun `an uncaught LlmException ends the task in Error with its message and no crash report`() =
        runTest {
            val task = mainTaskFixture(scriptClass = LlmNotConfiguredScript::class)

            val status = TaskExecutor(task) {}.run()

            val error = assertIs<TaskStatus.Error>(status)
            assertEquals("No AI provider is set up. Choose one in Settings → AI.", error.message)
            verify { logger.error("Error executing task: No AI provider is set up. Choose one in Settings → AI.") }
            verify(exactly = 0) { CrashReporter.report(any(), any()) }
        }

    @Test
    fun `a stopped task ends Idle and still finishes the script when onFinish suspends`() =
        runTest {
            val task = mainTaskFixture(scriptClass = SuspendingFinishScript::class)
            SuspendingFinishScript.reset()
            var status: TaskStatus? = null

            val job = launch(Dispatchers.Default) { status = TaskExecutor(task) {}.run() }
            SuspendingFinishScript.started.await()
            job.cancelAndJoin()

            assertIs<TaskStatus.Idle>(status)
            assertTrue(SuspendingFinishScript.finished)
        }
}

/** A script that calls an LLM with no provider set up and doesn't catch the failure. */
class LlmNotConfiguredScript : Script<ScriptConfiguration> {
    override suspend fun onStart(
        configuration: ScriptConfiguration,
        provider: ComponentProvider,
    ) = true

    override suspend fun execute(
        configuration: ScriptConfiguration,
        provider: ComponentProvider,
        statusUpdate: suspend (message: String) -> Unit,
    ): ExecutionResult = throw LlmException.NotConfigured()

    override suspend fun onFinish(
        configuration: ScriptConfiguration,
        provider: ComponentProvider,
    ) = Unit
}

/** A script that runs until stopped and suspends while cleaning up, like one that closes a browser. */
class SuspendingFinishScript : Script<ScriptConfiguration> {
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
    ) {
        delay(1)
        finished = true
    }

    companion object {
        lateinit var started: CompletableDeferred<Unit>

        @Volatile
        var finished = false

        fun reset() {
            started = CompletableDeferred()
            finished = false
        }
    }
}

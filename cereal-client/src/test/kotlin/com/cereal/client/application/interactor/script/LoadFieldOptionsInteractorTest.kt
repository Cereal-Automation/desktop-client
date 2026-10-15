package com.cereal.client.application.interactor.script

import com.cereal.client.application.exception.FieldOptionsTimeoutException
import com.cereal.client.application.exception.ScriptCallException
import com.cereal.sdk.FieldOption
import com.cereal.sdk.FieldOptions
import com.cereal.sdk.statemodifier.ScriptConfig
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class LoadFieldOptionsInteractorTest {
    private val interactor = LoadFieldOptionsInteractor()

    private fun options(load: suspend () -> List<FieldOption>) =
        object : FieldOptions {
            override val dependsOn: Set<String> = emptySet()

            override suspend fun load(config: ScriptConfig) = load()
        }

    private suspend fun result(fieldOptions: FieldOptions): SuspendableResult<List<FieldOption>, Exception> {
        var result: SuspendableResult<List<FieldOption>, Exception>? = null
        interactor(LoadFieldOptionsInteractor.Params(fieldOptions, mockk(relaxed = true))) { result = it }
        return result!!
    }

    @Test
    fun `returns the options the script loads`() =
        runTest {
            val result = result(options { listOf(FieldOption("a")) })

            assertEquals(listOf(FieldOption("a")), assertIs<SuspendableResult.Success<List<FieldOption>, Exception>>(result).value)
        }

    @Test
    fun `a script exception keeps its own message`() =
        runTest {
            val result = result(options { throw IllegalStateException("Invalid API key") })

            val error = assertIs<SuspendableResult.Failure<List<FieldOption>, Exception>>(result).error
            assertIs<ScriptCallException>(error)
            assertEquals("Invalid API key", error.message)
        }

    @Test
    fun `a load that outlasts the timeout fails with a timeout`() =
        runTest {
            val result = result(options { awaitCancellation() })

            assertIs<FieldOptionsTimeoutException>(assertIs<SuspendableResult.Failure<List<FieldOption>, Exception>>(result).error)
        }

    @Test
    fun `cancelling the caller cancels the load instead of reporting a failure`() =
        runTest {
            var reported: SuspendableResult<List<FieldOption>, Exception>? = null
            var cancelled = false
            val job =
                launch {
                    interactor(
                        LoadFieldOptionsInteractor.Params(
                            options {
                                try {
                                    awaitCancellation()
                                } catch (e: CancellationException) {
                                    cancelled = true
                                    throw e
                                }
                            },
                            mockk(relaxed = true),
                        ),
                    ) { reported = it }
                }
            testScheduler.runCurrent()
            job.cancel()
            testScheduler.advanceUntilIdle()

            assertTrue(cancelled)
            assertNull(reported)
        }
}

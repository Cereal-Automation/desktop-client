package com.cereal.client.application

import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.exception.CorruptDownloadException
import com.cereal.client.application.exception.InvalidSignatureException
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.IScope
import io.sentry.ITransaction
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.SpanStatus
import io.sentry.protocol.SentryId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InteractorTest {
    private lateinit var transaction: ITransaction

    @BeforeEach
    fun setUp() {
        transaction = mockk(relaxed = true)
        mockkStatic(Sentry::class)
        every { Sentry.startTransaction(any<String>(), any<String>()) } returns transaction
        every { Sentry.captureException(any<Throwable>(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Sentry::class)
    }

    @Test
    fun `invoke should start and finish a Sentry transaction on success`() =
        runTest {
            // Given
            val interactor =
                object : Interactor<String, Interactor.None>() {
                    override suspend fun run(params: Interactor.None) = "ok"
                }

            // When
            interactor(Interactor.None())

            // Then
            verify(exactly = 1) { Sentry.startTransaction(any<String>(), eq("interactor")) }
            verify(exactly = 1) { transaction.finish(SpanStatus.OK) }
        }

    @Test
    fun `invoke should finish transaction even when run throws`() =
        runTest {
            // Given
            val interactor =
                object : Interactor<String, Interactor.None>() {
                    override suspend fun run(params: Interactor.None): String = throw RuntimeException("boom")
                }
            var caughtException: Exception? = null

            // When
            interactor(Interactor.None()) { result ->
                result.fold({}, { caughtException = it })
            }

            // Then
            verify(exactly = 1) { transaction.finish(SpanStatus.INTERNAL_ERROR) }
            assertEquals(true, caughtException is CerealException)
        }

    @Test
    fun `invoke should rethrow CancellationException without reporting it or calling onResult`() =
        runTest {
            // Given: run is cancelled — normal control flow, not an error.
            val interactor =
                object : Interactor<String, Interactor.None>() {
                    override suspend fun run(params: Interactor.None): String = throw CancellationException("cancelled")
                }
            var onResultCalled = false

            // When / Then: cancellation propagates instead of being swallowed as a Failure.
            assertFailsWith<CancellationException> {
                interactor(Interactor.None()) { onResultCalled = true }
            }

            assertEquals(false, onResultCalled)
            verify(exactly = 0) { Sentry.captureException(any<Throwable>(), any<ScopeCallback>()) }
            verify(exactly = 1) { transaction.finish(SpanStatus.CANCELLED) }
        }

    @Test
    fun `invoke should use class simpleName as transaction name`() =
        runTest {
            // Given
            class MyNamedInteractor : Interactor<Unit, Interactor.None>() {
                override suspend fun run(params: Interactor.None) = Unit
            }
            val interactor = MyNamedInteractor()

            // When
            interactor(Interactor.None())

            // Then
            verify(exactly = 1) {
                Sentry.startTransaction("MyNamedInteractor", "interactor")
            }
        }

    @Test
    fun `invoke should call onResult with success when run succeeds`() =
        runTest {
            // Given
            val interactor =
                object : Interactor<String, Interactor.None>() {
                    override suspend fun run(params: Interactor.None) = "result"
                }
            var resultReceived: String? = null

            // When
            interactor(Interactor.None()) { result ->
                result.fold({ resultReceived = it }, {})
            }

            // Then
            assertEquals("result", resultReceived)
        }

    @Test
    fun `invoke should report InvalidSignatureException to Sentry and wrap it as CerealException`() =
        runTest {
            // Given
            val interactor =
                object : Interactor<String, Interactor.None>() {
                    override suspend fun run(params: Interactor.None): String =
                        throw InvalidSignatureException(
                            url = "https://example.com/api",
                            httpStatus = 200,
                            reason = InvalidSignatureException.REASON_MISMATCH,
                        )
                }
            var caughtException: Exception? = null

            // When
            interactor(Interactor.None()) { result ->
                result.fold({}, { caughtException = it })
            }

            // Then
            verify(exactly = 1) { Sentry.captureException(any<InvalidSignatureException>(), any<ScopeCallback>()) }
            assertEquals(true, caughtException is CerealException)
        }

    @Test
    fun `invoke should not report a plain IOException to Sentry`() =
        runTest {
            // Given: an ordinary network error, which is expected and should stay out of Sentry.
            val interactor =
                object : Interactor<String, Interactor.None>() {
                    override suspend fun run(params: Interactor.None): String = throw IOException("timeout")
                }
            var caughtException: Exception? = null

            // When
            interactor(Interactor.None()) { result ->
                result.fold({}, { caughtException = it })
            }

            // Then
            verify(exactly = 0) { Sentry.captureException(any<Throwable>(), any<ScopeCallback>()) }
            assertEquals(true, caughtException is CerealException)
        }

    @Test
    fun `invoke should map CorruptDownloadException to a distinct retry message without reporting to Sentry`() =
        runTest {
            // Given: a downloaded update whose hash didn't match the signed metadata — not a
            // connectivity problem, and (like a transient network error) not reported to Sentry.
            val interactor =
                object : Interactor<String, Interactor.None>() {
                    override suspend fun run(params: Interactor.None): String = throw CorruptDownloadException("SHA-256 mismatch: expected abc but was def")
                }
            var caughtException: Exception? = null

            // When
            interactor(Interactor.None()) { result ->
                result.fold({}, { caughtException = it })
            }

            // Then: the user is told to retry, not to check their connection.
            verify(exactly = 0) { Sentry.captureException(any<Throwable>(), any<ScopeCallback>()) }
            assertTrue(caughtException is CerealException)
            val message = caughtException?.message ?: ""
            assertTrue(message.contains("corrupted or out of date"), "should explain the file is corrupt/stale")
            assertTrue(!message.contains("check your connection"), "should not blame connectivity")
        }

    @Test
    fun `invoke should call onResult exactly once and not swallow a throwing success handler`() =
        runTest {
            // Given: run succeeds, but the caller's success handler throws.
            val interactor =
                object : Interactor<String, Interactor.None>() {
                    override suspend fun run(params: Interactor.None) = "ok"
                }
            var invocations = 0
            var lastResult: SuspendableResult<String, Exception>? = null

            // When
            val thrown =
                runCatching {
                    interactor(Interactor.None()) { result ->
                        invocations++
                        lastResult = result
                        throw RuntimeException("callback boom")
                    }
                }.exceptionOrNull()

            // Then: onResult ran once (with the success), and the handler's exception propagates
            // rather than being caught and re-dispatched as a bogus Failure.
            assertEquals(1, invocations)
            assertTrue(lastResult is SuspendableResult.Success)
            assertTrue(thrown is RuntimeException)
            assertEquals("callback boom", thrown.message)
        }

    @Test
    fun `invoke should redact sensitive params in the Sentry report`() =
        runTest {
            // Given: an interactor whose Params carry a secret and fail with an unexpected error.
            val scope = mockk<IScope>(relaxed = true)
            val scopeCallback = slot<ScopeCallback>()
            every { Sentry.captureException(any<Throwable>(), capture(scopeCallback)) } returns SentryId.EMPTY_ID
            val interactor =
                object : Interactor<String, SecretParams>() {
                    override suspend fun run(params: SecretParams): String = throw RuntimeException("boom")
                }

            // When
            interactor(SecretParams("hunter2")) {}
            scopeCallback.captured.run(scope)

            // Then: the plaintext password never reaches the Sentry context.
            verify { scope.setContexts("interactor_params", "<redacted: sensitive params>") }
        }

    @Test
    fun `invoke should attach non-sensitive params verbatim to the Sentry report`() =
        runTest {
            // Given
            val scope = mockk<IScope>(relaxed = true)
            val scopeCallback = slot<ScopeCallback>()
            every { Sentry.captureException(any<Throwable>(), capture(scopeCallback)) } returns SentryId.EMPTY_ID
            val interactor =
                object : Interactor<String, PlainParams>() {
                    override suspend fun run(params: PlainParams): String = throw RuntimeException("boom")
                }

            // When
            interactor(PlainParams("alice")) {}
            scopeCallback.captured.run(scope)

            // Then
            verify { scope.setContexts("interactor_params", "PlainParams(name=alice)") }
        }

    @Test
    fun `invoke should call onResult with failure when run throws CerealException`() =
        runTest {
            // Given
            val expected = CerealException("domain error")
            val interactor =
                object : Interactor<String, Interactor.None>() {
                    override suspend fun run(params: Interactor.None): String = throw expected
                }
            var caughtException: Exception? = null

            // When
            interactor(Interactor.None()) { result ->
                result.fold({}, { caughtException = it })
            }

            // Then
            assertEquals(expected, caughtException)
            verify(exactly = 1) { transaction.finish(SpanStatus.INTERNAL_ERROR) }
        }

    // --- FlowInteractor regression coverage --------------------------------------------------
    // A FlowInteractor's run() may do suspending work (e.g. a marketplace auth call) *before* it
    // returns the flow. toSuspendableResult only guards flow *collection*, so an exception thrown
    // by run() itself must be caught by invoke() and surfaced as a Failure — otherwise it escapes
    // uncaught and crashes the app. This was the shipped 1.5.1 crash: the marketplace
    // SignatureInterceptor threw `IOException("Invalid signature.")` from inside an auth
    // FlowInteractor's run(), which (before the invoke() guard) propagated to the coroutine's
    // uncaught handler and killed the process. These tests pin the base-class contract.

    @OptIn(FlowPreview::class)
    @Test
    fun `FlowInteractor invoke surfaces InvalidSignatureException as a reported Failure instead of crashing`() =
        runTest {
            // Given: run() throws before returning the flow, exactly as an auth interactor does when
            // the marketplace SignatureInterceptor rejects an unsigned/tampered response.
            val interactor =
                object : FlowInteractor<String, FlowInteractor.None>() {
                    override suspend fun run(params: FlowInteractor.None): Flow<String> =
                        throw InvalidSignatureException(
                            url = "https://marketplace.cereal-automation.com/api/subscription/me",
                            httpStatus = 200,
                            reason = InvalidSignatureException.REASON_HEADER_ABSENT,
                        )
                }

            // When: invoke() must not throw — it returns a flow that emits a single Failure.
            val results = interactor(FlowInteractor.None()).toList()

            // Then
            assertEquals(1, results.size)
            val failure = results.single()
            assertTrue(failure is SuspendableResult.Failure)
            var caughtException: Exception? = null
            failure.fold({}, { caughtException = it })
            assertTrue(caughtException is CerealException)
            verify(exactly = 1) { Sentry.captureException(any<InvalidSignatureException>(), any<ScopeCallback>()) }
        }

    @OptIn(FlowPreview::class)
    @Test
    fun `FlowInteractor invoke surfaces a run() network IOException as an unreported Failure`() =
        runTest {
            // Given: a plain connectivity error thrown by run()'s suspending work.
            val interactor =
                object : FlowInteractor<String, FlowInteractor.None>() {
                    override suspend fun run(params: FlowInteractor.None): Flow<String> = throw IOException("timeout")
                }

            // When
            val results = interactor(FlowInteractor.None()).toList()

            // Then: surfaced as a Failure (not an uncaught crash) and kept out of Sentry as expected noise.
            assertEquals(1, results.size)
            assertTrue(results.single() is SuspendableResult.Failure)
            verify(exactly = 0) { Sentry.captureException(any<Throwable>(), any<ScopeCallback>()) }
        }
}

private data class SecretParams(
    val password: String,
) : SensitiveParams

private data class PlainParams(
    val name: String,
)

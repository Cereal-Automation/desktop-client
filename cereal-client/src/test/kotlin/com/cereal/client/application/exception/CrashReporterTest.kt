package com.cereal.client.application.exception

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import io.sentry.IScope
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.protocol.SentryId
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.EOFException

class CrashReporterTest {
    @BeforeEach
    fun setUp() {
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any<Throwable>()) } returns SentryId.EMPTY_ID
        every { Sentry.captureException(any<Throwable>(), any<ScopeCallback>()) } returns SentryId.EMPTY_ID
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Sentry::class)
    }

    @Test
    fun `report forwards a genuine exception to Sentry`() {
        val error = RuntimeException("boom")

        CrashReporter.report(error)

        verify(exactly = 1) { Sentry.captureException(error) }
    }

    @Test
    fun `report drops known-noise exceptions filtered by ExceptionFilter`() {
        CrashReporter.report(EOFException())
        CrashReporter.report(CancellationException())

        verify(exactly = 0) { Sentry.captureException(any<Throwable>()) }
        verify(exactly = 0) { Sentry.captureException(any<Throwable>(), any<ScopeCallback>()) }
    }

    @Test
    fun `report attaches stringified context to the Sentry scope`() {
        val scope = mockk<IScope>(relaxed = true)
        val scopeCallback = slot<ScopeCallback>()
        every { Sentry.captureException(any<Throwable>(), capture(scopeCallback)) } returns SentryId.EMPTY_ID

        CrashReporter.report(RuntimeException("boom"), mapOf("entry" to "file.txt", "size" to 42))
        scopeCallback.captured.run(scope)

        verify { scope.setContexts("entry", "file.txt") }
        verify { scope.setContexts("size", "42") }
    }
}

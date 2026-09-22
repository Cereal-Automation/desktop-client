package com.cereal.client.application.exception

import io.sentry.SentryEvent
import io.sentry.protocol.SentryException
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExceptionFilterTest {
    private fun eventWith(
        type: String?,
        value: String? = null,
    ): SentryEvent =
        SentryEvent().apply {
            exceptions =
                mutableListOf(
                    SentryException().also {
                        it.type = type
                        it.value = value
                    },
                )
        }

    @Test
    fun `ignores EOFException`() {
        assertTrue(ExceptionFilter.shouldIgnoreSentryEvent(eventWith("java.io.EOFException")))
    }

    @Test
    fun `ignores kdriver shutdown hook IllegalAccessError`() {
        val event =
            eventWith(
                type = "java.lang.IllegalAccessError",
                value =
                    "failed to access class dev.kdriver.core.browser.Process_jvmKt\$addShutdownHook\$1\$1 " +
                        "from class dev.kdriver.core.browser.Process_jvmKt",
            )
        assertTrue(ExceptionFilter.shouldIgnoreSentryEvent(event))
    }

    @Test
    fun `does not ignore unrelated IllegalAccessError`() {
        val event =
            eventWith(
                type = "java.lang.IllegalAccessError",
                value = "failed to access class com.example.Foo from class com.example.Bar",
            )
        assertFalse(ExceptionFilter.shouldIgnoreSentryEvent(event))
    }

    @Test
    fun `does not ignore unrelated exceptions`() {
        assertFalse(ExceptionFilter.shouldIgnoreSentryEvent(eventWith("java.lang.NullPointerException")))
    }
}

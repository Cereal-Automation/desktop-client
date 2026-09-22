package com.cereal.client.presentation.error

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ErrorResolverTest {
    @Test
    fun `starts with no error`() {
        val resolver = ErrorResolver()

        assertSame(ErrorAction.None, resolver.errorAction.value)
    }

    @Test
    fun `setError with a message exposes a dismissible message action`() {
        val resolver = ErrorResolver()

        resolver.setError("Something broke")

        val action = resolver.errorAction.value as ErrorAction.Message
        assertEquals("Something broke", action.message)

        action.dismiss()
        assertSame(ErrorAction.None, resolver.errorAction.value)
    }

    @Test
    fun `setError with an exception uses its localized message`() {
        val resolver = ErrorResolver()

        resolver.setError(IllegalStateException("boom"))

        val action = resolver.errorAction.value as ErrorAction.Message
        assertEquals("boom", action.message)
    }

    @Test
    fun `setError falls back to the default message when the exception has none`() {
        val resolver = ErrorResolver()

        resolver.setError(RuntimeException())

        val action = resolver.errorAction.value as ErrorAction.Message
        assertEquals(DEFAULT_ERROR_MESSAGE, action.message)
    }

    @Test
    fun `reset clears the current error`() {
        val resolver = ErrorResolver()
        resolver.setError("oops")
        assertTrue(resolver.errorAction.value is ErrorAction.Message)

        resolver.reset()

        assertSame(ErrorAction.None, resolver.errorAction.value)
    }
}

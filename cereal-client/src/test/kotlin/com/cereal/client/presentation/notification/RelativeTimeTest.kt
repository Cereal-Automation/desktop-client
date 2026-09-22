package com.cereal.client.presentation.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class RelativeTimeTest {
    private val now = 1_000_000_000_000L

    private fun ago(
        amount: Long,
        unit: TimeUnit,
    ) = formatRelativeTime(now - unit.toMillis(amount), now)

    @Test
    fun `under a minute reads as just now`() {
        assertEquals("Just now", ago(30, TimeUnit.SECONDS))
    }

    @Test
    fun `minutes are rendered with an m suffix`() {
        assertEquals("5m ago", ago(5, TimeUnit.MINUTES))
    }

    @Test
    fun `hours are rendered with an h suffix`() {
        assertEquals("2h ago", ago(2, TimeUnit.HOURS))
    }

    @Test
    fun `one day reads as yesterday`() {
        assertEquals("Yesterday", ago(1, TimeUnit.DAYS))
    }

    @Test
    fun `several days are rendered with a d suffix`() {
        assertEquals("3d ago", ago(3, TimeUnit.DAYS))
    }
}

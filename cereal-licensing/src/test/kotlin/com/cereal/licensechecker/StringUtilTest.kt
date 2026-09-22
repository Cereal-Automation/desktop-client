package com.cereal.licensechecker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [getRandomString]. The salt it produces feeds signature verification, so its
 * length, character set, and unpredictability are correctness- and security-relevant.
 */
class StringUtilTest {
    @Test
    fun `produces a string of the requested length`() {
        assertEquals(32, getRandomString(32).length)
        assertEquals(1, getRandomString(1).length)
        assertEquals(128, getRandomString(128).length)
    }

    @Test
    fun `produces an empty string for length zero`() {
        assertEquals("", getRandomString(0))
    }

    @Test
    fun `produces only alphanumeric characters`() {
        val allowed = (('A'..'Z') + ('a'..'z') + ('0'..'9')).toSet()

        val generated = getRandomString(500)

        assertTrue(
            generated.all { it in allowed },
            "Expected only [A-Za-z0-9] but got: $generated",
        )
    }

    @Test
    fun `produces different values across calls`() {
        // With a 62-character alphabet and length 32 the collision probability is astronomically
        // small, so distinct values is a reliable proxy for "uses randomness".
        val values = (1..100).map { getRandomString(32) }.toSet()

        assertEquals(100, values.size, "Generated salts should be unique")
    }
}

package com.cereal.client.presentation.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InitialsTest {
    @Test
    fun `returns the fallback for null or blank names`() {
        assertEquals("?", initialsOf(null))
        assertEquals("?", initialsOf(""))
        assertEquals("?", initialsOf("   "))
        assertEquals("X", initialsOf(null, fallback = 'x'))
    }

    @Test
    fun `takes up to two characters from a single word`() {
        assertEquals("AB", initialsOf("abc"))
        assertEquals("A", initialsOf("a"))
    }

    @Test
    fun `takes the first character of the first two words`() {
        assertEquals("JD", initialsOf("john doe"))
        assertEquals("JD", initialsOf("john_doe"))
        assertEquals("JD", initialsOf("john-doe"))
        assertEquals("JD", initialsOf("john.doe"))
        assertEquals("JD", initialsOf("john(doe)"))
    }

    @Test
    fun `trims surrounding whitespace before computing initials`() {
        assertEquals("JD", initialsOf("  john   doe  "))
    }

    @Test
    fun `falls back to the first character when only separators remain`() {
        assertEquals("_", initialsOf("___"))
    }
}

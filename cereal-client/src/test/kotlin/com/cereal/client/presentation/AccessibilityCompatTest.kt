package com.cereal.client.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AccessibilityCompatTest {
    @Test
    fun `returns null when nothing is configured`() {
        assertNull(resolveAvailableAssistiveTechnologies(null) { true })
    }

    @Test
    fun `returns null when configured value is blank`() {
        assertNull(resolveAvailableAssistiveTechnologies("   ") { false })
    }

    @Test
    fun `returns null when all configured classes load`() {
        val configured = "com.example.Reader,com.example.Magnifier"

        assertNull(resolveAvailableAssistiveTechnologies(configured) { true })
    }

    @Test
    fun `keeps only loadable classes when one is unavailable`() {
        val configured = "com.example.Reader,com.example.Missing"

        val result = resolveAvailableAssistiveTechnologies(configured) { it != "com.example.Missing" }

        assertEquals("com.example.Reader", result)
    }

    @Test
    fun `returns empty string for the missing AccessBridge case`() {
        val configured = "com.sun.java.accessibility.AccessBridge"

        val result = resolveAvailableAssistiveTechnologies(configured) { false }

        assertEquals("", result)
    }

    @Test
    fun `trims whitespace and ignores empty entries from trailing commas`() {
        val configured = " com.example.Reader , , com.example.Missing ,"

        val result = resolveAvailableAssistiveTechnologies(configured) { it == "com.example.Reader" }

        assertEquals("com.example.Reader", result)
    }
}

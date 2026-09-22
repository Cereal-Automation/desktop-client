package com.cereal.client.smoke

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmokeTestTest {
    @Test
    fun `isRequested is true for the documented truthy values`() {
        assertTrue(SmokeTest.isRequested("1"))
        assertTrue(SmokeTest.isRequested("true"))
        assertTrue(SmokeTest.isRequested("TRUE"))
        assertTrue(SmokeTest.isRequested(" yes "))
    }

    @Test
    fun `isRequested is false when unset or falsy`() {
        assertFalse(SmokeTest.isRequested(null))
        assertFalse(SmokeTest.isRequested(""))
        assertFalse(SmokeTest.isRequested("0"))
        assertFalse(SmokeTest.isRequested("false"))
        assertFalse(SmokeTest.isRequested("nope"))
    }
}

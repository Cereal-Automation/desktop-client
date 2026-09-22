package com.cereal.client.domain.model.script

import com.cereal.client.domain.model.exception.InvalidScriptCapacityException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ScriptCapacityTest {
    @Test
    fun `of returns None when the unit is null even if a cap is present`() {
        // A cap with no unit is a contract contradiction; it is normalised away.
        assertEquals(ScriptCapacity.None, ScriptCapacity.of(capacity = 100, unit = null))
    }

    @Test
    fun `of returns None when both the cap and unit are null`() {
        assertEquals(ScriptCapacity.None, ScriptCapacity.of(capacity = null, unit = null))
    }

    @Test
    fun `of returns Unlimited when the unit is present but the cap is null`() {
        assertEquals(ScriptCapacity.Unlimited("records"), ScriptCapacity.of(capacity = null, unit = "records"))
    }

    @Test
    fun `of returns Limited when both the unit and cap are present`() {
        assertEquals(ScriptCapacity.Limited(100, "records"), ScriptCapacity.of(capacity = 100, unit = "records"))
    }

    @Test
    fun `Limited allows a zero cap`() {
        assertEquals(0, ScriptCapacity.Limited(0, "records").records)
    }

    @Test
    fun `Limited throws when records is negative`() {
        val exception = assertThrows<InvalidScriptCapacityException> { ScriptCapacity.Limited(-1, "records") }
        assertEquals("records cannot be negative", exception.message)
    }
}

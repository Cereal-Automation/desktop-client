package com.cereal.client.presentation.view.fields.state

import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DropDownFieldStateTest {
    @Test
    fun `showClearButton returns false when clearable is false and value is selected`() {
        val state =
            DropDownFieldState(
                initialValues = listOf("A", "B"),
                clearable = false,
                initialSelectedValue = "A",
            )
        assertFalse(state.showClearButton())
    }

    @Test
    fun `showClearButton returns false when clearable is true but no value is selected`() {
        val state =
            DropDownFieldState(
                initialValues = listOf("A", "B"),
                clearable = true,
                initialSelectedValue = null,
            )
        assertFalse(state.showClearButton())
    }

    @Test
    fun `showClearButton returns true when clearable is true and value is selected`() {
        val state =
            DropDownFieldState(
                initialValues = listOf("A", "B"),
                clearable = true,
                initialSelectedValue = "A",
            )
        assertTrue(state.showClearButton())
    }

    @Test
    fun `onClear sets selectedValue to null and invokes onValueChange callback`() {
        val slot = slot<DropDownFieldState<String>>()
        val callback = mockk<(DropDownFieldState<String>) -> Unit>(relaxed = true)
        val state =
            DropDownFieldState(
                initialValues = listOf("A", "B"),
                clearable = true,
                initialSelectedValue = "A",
                onValueChange = callback,
            )

        state.onClear()

        verify(exactly = 1) { callback(capture(slot)) }
        assertNull(slot.captured.selectedValue)
    }

    @Test
    fun `showClearButton returns false after onClear is called`() {
        val state =
            DropDownFieldState(
                initialValues = listOf("A", "B"),
                clearable = true,
                initialSelectedValue = "A",
            )

        state.onClear()

        assertFalse(state.showClearButton())
    }

    @Test
    fun `onClear does nothing when clearable is false`() {
        val callback = mockk<(DropDownFieldState<String>) -> Unit>(relaxed = true)
        val state =
            DropDownFieldState(
                initialValues = listOf("A", "B"),
                clearable = false,
                initialSelectedValue = "A",
                onValueChange = callback,
            )

        state.onClear()

        assertEquals("A", state.selectedValue)
        verify(exactly = 0) { callback(any()) }
    }
}

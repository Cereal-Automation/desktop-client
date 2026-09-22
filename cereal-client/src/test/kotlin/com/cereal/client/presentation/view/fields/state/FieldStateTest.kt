package com.cereal.client.presentation.view.fields.state

import com.cereal.client.presentation.view.fields.validator.BooleanFieldValidator
import com.cereal.client.presentation.view.fields.validator.FileFieldValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

private fun booleanValidator(result: String?) =
    object : BooleanFieldValidator {
        override fun validate(value: Boolean?): String? = result
    }

private fun fileValidator(result: String?) =
    object : FileFieldValidator {
        override fun validate(value: File?): String? = result
    }

class FieldStateTest {
    @Test
    fun `switch field exposes its checked value and notifies changes`() {
        var notified: SwitchFieldState? = null
        val state = SwitchFieldState(initialValue = false, onValueChange = { notified = it })

        assertFalse(state.isChecked)
        assertFalse(state.getValidatedValue())

        state.onValueChange(true)

        assertTrue(state.isChecked)
        assertTrue(state.getValidatedValue())
        assertSameInstance(state, notified)
    }

    @Test
    fun `file field exposes its selected file and notifies changes`() {
        var notified: FileFieldState? = null
        val state = FileFieldState(onValueChange = { notified = it })
        val file = File("/tmp/data.csv")

        assertNull(state.selectedFile)
        assertNull(state.getValidatedValue())

        state.onValueChange(file)

        assertEquals(file, state.selectedFile)
        assertEquals(file, state.getValidatedValue())
        assertSameInstance(state, notified)
    }

    @Test
    fun `validate captures the first validator error and clears on reset`() {
        val state =
            SwitchFieldState(
                validators = listOf(booleanValidator("always invalid")),
            )

        assertFalse(state.validate())
        assertTrue(state.showErrors())
        assertEquals("always invalid", state.error)

        state.resetValidationErrors()
        assertFalse(state.showErrors())
        assertNull(state.error)
    }

    @Test
    fun `validate returns true and keeps no error when validators pass`() {
        val state = FileFieldState(validators = listOf(fileValidator(null)))

        assertTrue(state.validate())
        assertNull(state.error)
    }

    @Test
    fun `focus change validates only after the field was focused and blurred`() {
        val state = SwitchFieldState(validators = listOf(booleanValidator("invalid")))

        // Blurring without ever focusing must not run validation.
        state.onFocusChange(false)
        assertNull(state.error)

        // Focus then blur triggers validation.
        state.onFocusChange(true)
        state.onFocusChange(false)
        assertEquals("invalid", state.error)
    }

    @Test
    fun `visibility and required flags default and can be toggled`() {
        val state = SwitchFieldState()

        assertTrue(state.isVisible.value)
        assertFalse(state.isRequired.value)

        state.isVisible.value = false
        state.isRequired.value = true

        assertFalse(state.isVisible.value)
        assertTrue(state.isRequired.value)
    }

    private fun assertSameInstance(
        expected: Any?,
        actual: Any?,
    ) {
        assertTrue(expected === actual, "expected the same instance to be reported")
    }
}

package com.cereal.client.presentation.view.fields.validator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IntStringFieldValidatorTest {
    @Test
    fun `rejects non-numeric input`() {
        assertNotNull(IntStringFieldValidator().validate("abc"))
        assertNotNull(IntStringFieldValidator().validate(null))
    }

    @Test
    fun `accepts a plain integer with no bounds`() {
        assertNull(IntStringFieldValidator().validate("42"))
    }

    @Test
    fun `enforces the minimum value`() {
        val validator = IntStringFieldValidator(minValue = 10)

        assertEquals("Minimum allowed value is 10", validator.validate("5"))
        assertNull(validator.validate("10"))
    }

    @Test
    fun `enforces the maximum value`() {
        val validator = IntStringFieldValidator(maxValue = 100)

        assertEquals("Maximum allowed value is 100", validator.validate("101"))
        assertNull(validator.validate("100"))
    }
}

class PasswordStrengthValidatorTest {
    private val validator = PasswordStrengthValidator()

    @Test
    fun `rejects a blank password`() {
        assertEquals("Password is required", validator.validate(null))
        assertEquals("Password is required", validator.validate("   "))
    }

    @Test
    fun `accepts a strong password`() {
        assertNull(validator.validate("Str0ngPass"))
    }

    @Test
    fun `lists the missing requirements for a weak password`() {
        val error = validator.validate("abc")

        assertNotNull(error)
        requireNotNull(error)
        assertTrue(error.contains("at least 8 characters")) { error }
        assertTrue(error.contains("an uppercase letter")) { error }
        assertTrue(error.contains("a number")) { error }
    }
}

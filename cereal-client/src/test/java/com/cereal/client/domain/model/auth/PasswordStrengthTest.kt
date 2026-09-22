package com.cereal.client.domain.model.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PasswordStrengthTest {
    @Test
    fun `evaluate empty password`() {
        val strength = PasswordStrength.evaluate("")

        assertFalse(strength.hasMinimumLength)
        assertFalse(strength.hasUppercase)
        assertFalse(strength.hasLowercase)
        assertFalse(strength.hasNumber)
        assertFalse(strength.isValid)
        assertEquals(PasswordStrength.Strength.WEAK, strength.strength)
    }

    @Test
    fun `evaluate fair password - only lowercase`() {
        val strength = PasswordStrength.evaluate("password")

        assertTrue(strength.hasMinimumLength)
        assertFalse(strength.hasUppercase)
        assertTrue(strength.hasLowercase)
        assertFalse(strength.hasNumber)
        assertFalse(strength.isValid)
        assertEquals(PasswordStrength.Strength.FAIR, strength.strength)
    }

    @Test
    fun `evaluate good password - missing number`() {
        val strength = PasswordStrength.evaluate("Password")

        assertTrue(strength.hasMinimumLength)
        assertTrue(strength.hasUppercase)
        assertTrue(strength.hasLowercase)
        assertFalse(strength.hasNumber)
        assertFalse(strength.isValid)
        assertEquals(PasswordStrength.Strength.GOOD, strength.strength)
    }

    @Test
    fun `evaluate strong password - all requirements met`() {
        val strength = PasswordStrength.evaluate("Password123")

        assertTrue(strength.hasMinimumLength)
        assertTrue(strength.hasUppercase)
        assertTrue(strength.hasLowercase)
        assertTrue(strength.hasNumber)
        assertTrue(strength.isValid)
        assertEquals(PasswordStrength.Strength.STRONG, strength.strength)
    }

    @Test
    fun `evaluate password with minimum length exactly`() {
        val strength = PasswordStrength.evaluate("Pass1!")

        assertFalse(strength.hasMinimumLength) // Only 6 chars
        assertFalse(strength.isValid)
    }

    @Test
    fun `evaluate password with exactly 8 characters`() {
        val strength = PasswordStrength.evaluate("Pass12ab")

        assertTrue(strength.hasMinimumLength) // Exactly 8 chars
        assertTrue(strength.hasUppercase)
        assertTrue(strength.hasLowercase)
        assertTrue(strength.hasNumber)
        assertTrue(strength.isValid)
        assertEquals(PasswordStrength.Strength.STRONG, strength.strength)
    }

    @Test
    fun `evaluate password with spaces`() {
        val strength = PasswordStrength.evaluate("Pass word1")

        assertTrue(strength.hasMinimumLength)
        assertTrue(strength.hasUppercase)
        assertTrue(strength.hasLowercase)
        assertTrue(strength.hasNumber)
        assertTrue(strength.isValid)
    }
}

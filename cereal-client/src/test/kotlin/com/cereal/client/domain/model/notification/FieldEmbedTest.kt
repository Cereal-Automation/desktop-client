package com.cereal.client.domain.model.notification

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FieldEmbedTest {
    @Test
    fun `should create valid FieldEmbed with name and value`() {
        val field = FieldEmbed(name = "Field Name", value = "Field Value")
        assertNotNull(field)
    }

    @Test
    fun `should fail when name is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                FieldEmbed(name = "   ", value = "Value")
            }
        assertEquals("Field name cannot be blank", exception.message)
    }

    @Test
    fun `should fail when name exceeds 256 characters`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                FieldEmbed(name = "a".repeat(257), value = "Value")
            }
        assertEquals("Field name cannot exceed 256 characters", exception.message)
    }

    @Test
    fun `should accept name with exactly 256 characters`() {
        val field = FieldEmbed(name = "a".repeat(256), value = "Value")
        assertNotNull(field)
    }

    @Test
    fun `should fail when value is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                FieldEmbed(name = "Name", value = "   ")
            }
        assertEquals("Field value cannot be blank", exception.message)
    }

    @Test
    fun `should fail when value exceeds 1024 characters`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                FieldEmbed(name = "Name", value = "a".repeat(1025))
            }
        assertEquals("Field value cannot exceed 1024 characters", exception.message)
    }

    @Test
    fun `should accept value with exactly 1024 characters`() {
        val field = FieldEmbed(name = "Name", value = "a".repeat(1024))
        assertNotNull(field)
    }

    @Test
    fun `should create field with inline flag true`() {
        val field = FieldEmbed(name = "Name", value = "Value", inline = true)
        assertNotNull(field)
        assertEquals(true, field.inline)
    }

    @Test
    fun `should create field with inline flag false`() {
        val field = FieldEmbed(name = "Name", value = "Value", inline = false)
        assertNotNull(field)
        assertEquals(false, field.inline)
    }

    @Test
    fun `should create field with null inline flag`() {
        val field = FieldEmbed(name = "Name", value = "Value", inline = null)
        assertNotNull(field)
        assertEquals(null, field.inline)
    }

    @Test
    fun `should fail when both name and value are blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                FieldEmbed(name = "   ", value = "   ")
            }
        assertEquals("Field name cannot be blank", exception.message)
    }
}

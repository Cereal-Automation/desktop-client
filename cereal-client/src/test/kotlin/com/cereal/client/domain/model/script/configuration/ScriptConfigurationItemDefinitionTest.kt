package com.cereal.client.domain.model.script.configuration

import com.cereal.client.domain.model.exception.UnsupportedConfigurationTypeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScriptConfigurationItemDefinitionTest {
    private fun definitionOf(type: ConfigItemType): ScriptConfigurationItemDefinition =
        ScriptConfigurationItemDefinition(
            name = "name",
            description = "description",
            key = "key",
            position = 0,
            type = type,
            isNullable = false,
            stateModifier = null,
            isScriptIdentifier = false,
        )

    // region containsValidData

    @Test
    fun `containsValidData is true for a non-empty string`() {
        val definition = definitionOf(ConfigItemType.StringConfigItem)
        assertTrue(definition.containsValidData(ConfigValue.StringValue("value")))
    }

    @Test
    fun `containsValidData is false for an empty string`() {
        val definition = definitionOf(ConfigItemType.StringConfigItem)
        assertFalse(definition.containsValidData(ConfigValue.StringValue("")))
    }

    @Test
    fun `containsValidData is always true for a boolean item`() {
        val definition = definitionOf(ConfigItemType.BooleanConfigItem)
        assertTrue(definition.containsValidData(ConfigValue.BooleanValue(false)))
    }

    @Test
    fun `containsValidData is true for a non-empty secret`() {
        val definition = definitionOf(ConfigItemType.SecretConfigItem)
        assertTrue(definition.containsValidData(ConfigValue.SecretValue(Secret("token"))))
    }

    @Test
    fun `containsValidData is false for an empty secret, matching the string rule`() {
        val definition = definitionOf(ConfigItemType.SecretConfigItem)
        assertFalse(definition.containsValidData(ConfigValue.SecretValue(Secret(""))))
    }

    // endregion

    // region isValidReturnType

    @Test
    fun `isValidReturnType is true when the raw type matches the script value type`() {
        val definition = definitionOf(ConfigItemType.StringConfigItem)
        assertTrue(definition.isValidReturnType(ConfigValue.StringValue("value")))
    }

    @Test
    fun `isValidReturnType is false when the raw type does not match`() {
        val definition = definitionOf(ConfigItemType.StringConfigItem)
        assertFalse(definition.isValidReturnType(ConfigValue.IntValue(1)))
    }

    @Test
    fun `isValidReturnType is true for a secret item given a secret value`() {
        val definition = definitionOf(ConfigItemType.SecretConfigItem)
        assertTrue(definition.isValidReturnType(ConfigValue.SecretValue(Secret("token"))))
    }

    @Test
    fun `isValidReturnType is false for a secret item given a bare string value`() {
        val definition = definitionOf(ConfigItemType.SecretConfigItem)
        assertFalse(definition.isValidReturnType(ConfigValue.StringValue("token")))
    }

    // endregion

    // region parseValue

    @Test
    fun `parseValue parses an Int`() {
        val definition = definitionOf(ConfigItemType.IntConfigItem)
        assertEquals(ConfigValue.IntValue(42), definition.parseValue("42"))
    }

    @Test
    fun `parseValue wraps raw text into a secret value for a secret item`() {
        // Raw text arriving from an imported CSV cell. Wrapped on the way in, so the credential is
        // masked from the moment it enters the domain.
        val definition = definitionOf(ConfigItemType.SecretConfigItem)
        assertEquals(ConfigValue.SecretValue(Secret("sk-live-token")), definition.parseValue("sk-live-token"))
    }

    @Test
    fun `parseValue returns null for a secret item given no value`() {
        val definition = definitionOf(ConfigItemType.SecretConfigItem)
        assertNull(definition.parseValue(null))
    }

    @Test
    fun `parseValue parses a Boolean`() {
        val definition = definitionOf(ConfigItemType.BooleanConfigItem)
        assertEquals(ConfigValue.BooleanValue(true), definition.parseValue("true"))
    }

    @Test
    fun `parseValue parses a Float`() {
        val definition = definitionOf(ConfigItemType.FloatConfigItem)
        assertEquals(ConfigValue.FloatValue(1.5f), definition.parseValue("1.5"))
    }

    @Test
    fun `parseValue parses a Double`() {
        val definition = definitionOf(ConfigItemType.DoubleConfigItem)
        assertEquals(ConfigValue.DoubleValue(2.5), definition.parseValue("2.5"))
    }

    @Test
    fun `parseValue returns the raw string for a string item`() {
        val definition = definitionOf(ConfigItemType.StringConfigItem)
        assertEquals(ConfigValue.StringValue("hello"), definition.parseValue("hello"))
    }

    @Test
    fun `parseValue returns null for a null raw value`() {
        val definition = definitionOf(ConfigItemType.IntConfigItem)
        assertNull(definition.parseValue(null))
    }

    @Test
    fun `parseValue throws NumberFormatException for an unparseable number`() {
        val definition = definitionOf(ConfigItemType.IntConfigItem)
        assertThrows(NumberFormatException::class.java) {
            definition.parseValue("not-a-number")
        }
    }

    @Test
    fun `parseValue throws for an unsupported type`() {
        val definition = definitionOf(ConfigItemType.ProxyConfigItem)
        assertThrows(UnsupportedConfigurationTypeException::class.java) {
            definition.parseValue("anything")
        }
    }

    // endregion
}

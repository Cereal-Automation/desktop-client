package com.cereal.client.domain.model.script.configuration

import com.cereal.client.domain.model.exception.UnsupportedConfigurationTypeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ParseValueTest {
    private enum class Color { RED, GREEN }

    private fun definition(type: ConfigItemType) =
        ScriptConfigurationItemDefinition(
            name = "field",
            description = "",
            key = "field",
            position = 0,
            type = type,
            isNullable = true,
            stateModifier = null,
            isScriptIdentifier = false,
        )

    @Test
    fun `parses int, float, double, boolean and string values`() {
        assertEquals(ConfigValue.IntValue(42), definition(ConfigItemType.IntConfigItem).parseValue("42"))
        assertEquals(ConfigValue.FloatValue(1.5f), definition(ConfigItemType.FloatConfigItem).parseValue("1.5"))
        assertEquals(ConfigValue.DoubleValue(2.5), definition(ConfigItemType.DoubleConfigItem).parseValue("2.5"))
        assertEquals(ConfigValue.BooleanValue(true), definition(ConfigItemType.BooleanConfigItem).parseValue("true"))
        assertEquals(ConfigValue.StringValue("hello"), definition(ConfigItemType.StringConfigItem).parseValue("hello"))
    }

    @Test
    fun `parses a matching enum constant`() {
        assertEquals(ConfigValue.EnumValue(Color.RED), enumDefinition().parseValue("RED"))
    }

    @Test
    fun `rejects an enum value matching no constant, naming the ones that do`() {
        // Previously this returned null, which discarded the value on a nullable field and produced a
        // misleading "empty but required" on a non-nullable one.
        val error =
            assertFailsWith<IllegalArgumentException> {
                enumDefinition().parseValue("PURPLE")
            }

        assertEquals("'PURPLE' is not one of: RED, GREEN.", error.message)
    }

    @Test
    fun `accepts every unambiguous boolean spelling, whatever its case`() {
        val booleanDefinition = definition(ConfigItemType.BooleanConfigItem)

        listOf("true", "TRUE", "yes", "Yes", "1").forEach {
            assertEquals(ConfigValue.BooleanValue(true), booleanDefinition.parseValue(it), it)
        }
        listOf("false", "False", "no", "NO", "0").forEach {
            assertEquals(ConfigValue.BooleanValue(false), booleanDefinition.parseValue(it), it)
        }
    }

    @Test
    fun `rejects an unrecognised boolean rather than silently reading it as false`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                definition(ConfigItemType.BooleanConfigItem).parseValue("maybe")
            }

        assertEquals("'maybe' is not a yes/no value. Accepted: true, yes, 1, false, no, 0.", error.message)
    }

    @Test
    fun `tolerates surrounding whitespace on the typed values`() {
        assertEquals(ConfigValue.IntValue(42), definition(ConfigItemType.IntConfigItem).parseValue(" 42 "))
        assertEquals(ConfigValue.BooleanValue(true), definition(ConfigItemType.BooleanConfigItem).parseValue(" yes "))
        assertEquals(ConfigValue.EnumValue(Color.GREEN), enumDefinition().parseValue(" GREEN "))
    }

    @Suppress("UNCHECKED_CAST")
    private fun enumDefinition() = definition(ConfigItemType.EnumConfigItem(Color::class as kotlin.reflect.KClass<Enum<*>>))

    @Test
    fun `returns null when raw value is null`() {
        assertNull(definition(ConfigItemType.IntConfigItem).parseValue(null))
        assertNull(definition(ConfigItemType.StringConfigItem).parseValue(null))
    }

    @Test
    fun `throws NumberFormatException for an invalid number`() {
        assertFailsWith<NumberFormatException> {
            definition(ConfigItemType.IntConfigItem).parseValue("not-a-number")
        }
    }

    @Test
    fun `throws for unsupported configuration types`() {
        assertFailsWith<UnsupportedConfigurationTypeException> {
            definition(ConfigItemType.ProxyConfigItem).parseValue("a")
        }
    }
}

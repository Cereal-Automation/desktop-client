package com.cereal.client.presentation.tasks.script.overview.configuration

import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.statemodifier.DefaultStateModifier
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScriptConfigurationDefinitionExtensionsTest {
    @Test
    fun `validate returns invalid for required field without value and without default`() {
        val definition =
            createDefinition(
                isNullable = false,
                defaultValue = null,
            )
        val values = emptyMap<String, ConfigValue>()

        val invalidItems = definition.validate(values)

        assertFalse(invalidItems.isEmpty())
        assertTrue(invalidItems.any { it.key == "testKey" })
    }

    @Test
    fun `validate returns valid for required field with default value but no explicit value`() {
        val definition =
            createDefinition(
                isNullable = false,
                defaultValue = ConfigValue.StringValue("default"),
            )
        val values = emptyMap<String, ConfigValue>()

        val invalidItems = definition.validate(values)

        assertTrue(invalidItems.isEmpty())
    }

    @Test
    fun `validate returns valid for required field with explicit value`() {
        val definition =
            createDefinition(
                isNullable = false,
                defaultValue = null,
            )
        val values = mapOf("testKey" to ConfigValue.StringValue("explicitValue"))

        val invalidItems = definition.validate(values)

        assertTrue(invalidItems.isEmpty())
    }

    @Test
    fun `validate returns valid for nullable field without value and without default`() {
        val definition =
            createDefinition(
                isNullable = true,
                defaultValue = null,
            )
        val values = emptyMap<String, ConfigValue>()

        val invalidItems = definition.validate(values)

        assertTrue(invalidItems.isEmpty())
    }

    @Test
    fun `isValid returns true for required field with default value but no explicit value`() {
        val definition =
            createDefinition(
                isNullable = false,
                defaultValue = ConfigValue.StringValue("default"),
            )
        val values = emptyMap<String, ConfigValue>()

        assertTrue(definition.isValid(values))
    }

    @Test
    fun `isValid returns false for required field without value and without default`() {
        val definition =
            createDefinition(
                isNullable = false,
                defaultValue = null,
            )
        val values = emptyMap<String, ConfigValue>()

        assertFalse(definition.isValid(values))
    }

    private fun createDefinition(
        isNullable: Boolean,
        defaultValue: ConfigValue?,
    ): ScriptConfigurationDefinition {
        val itemDefinition =
            ScriptConfigurationItemDefinition(
                name = "Test Field",
                description = "A test field",
                key = "testKey",
                position = 0,
                type = ConfigItemType.StringConfigItem,
                isNullable = isNullable,
                stateModifier = DefaultStateModifier,
                isScriptIdentifier = false,
                defaultValue = defaultValue,
            )

        return ScriptConfigurationDefinition(
            scriptConfigurationClass = ScriptConfiguration::class,
            configurationItems = listOf(itemDefinition),
        )
    }
}

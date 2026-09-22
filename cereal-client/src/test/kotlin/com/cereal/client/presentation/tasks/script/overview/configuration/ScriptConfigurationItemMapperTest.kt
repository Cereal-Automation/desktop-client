package com.cereal.client.presentation.tasks.script.overview.configuration

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.client.presentation.view.fields.state.DoubleTextFieldState
import com.cereal.client.presentation.view.fields.state.DropDownFieldState
import com.cereal.client.presentation.view.fields.state.FloatTextFieldState
import com.cereal.client.presentation.view.fields.state.IntTextFieldState
import com.cereal.client.presentation.view.fields.state.SecretTextFieldState
import com.cereal.client.presentation.view.fields.state.StringTextFieldState
import com.cereal.client.presentation.view.fields.state.SwitchFieldState
import com.cereal.sdk.statemodifier.ScriptConfig
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime

class ScriptConfigurationItemMapperTest {
    private val scriptConfigProvider: () -> ScriptConfig = { mockk(relaxed = true) }

    private fun definition(type: ConfigItemType) =
        ScriptConfigurationItemDefinition(
            name = "Field",
            description = "A field",
            key = "field",
            position = 0,
            type = type,
            isNullable = false,
            stateModifier = null,
            isScriptIdentifier = false,
        )

    private fun ConfigurationItem.toState() =
        createFormFieldState(
            onValueChange = {},
            scriptConfigProvider = scriptConfigProvider,
            onImportFile = { _, _ -> },
        )

    @Test
    fun `boolean item maps to a switch field state`() {
        assertTrue(
            ConfigurationItem(definition(ConfigItemType.BooleanConfigItem), ConfigValue.BooleanValue(true)).toState()
                is SwitchFieldState,
        )
    }

    @Test
    fun `numeric items map to their text field states`() {
        assertTrue(
            ConfigurationItem(definition(ConfigItemType.IntConfigItem), ConfigValue.IntValue(1)).toState() is IntTextFieldState,
        )
        assertTrue(
            ConfigurationItem(definition(ConfigItemType.FloatConfigItem), ConfigValue.FloatValue(1f)).toState() is FloatTextFieldState,
        )
        assertTrue(
            ConfigurationItem(definition(ConfigItemType.DoubleConfigItem), ConfigValue.DoubleValue(1.0)).toState()
                is DoubleTextFieldState,
        )
    }

    @Test
    fun `string item maps to a string text field state`() {
        assertTrue(
            ConfigurationItem(definition(ConfigItemType.StringConfigItem), ConfigValue.StringValue("x")).toState()
                is StringTextFieldState,
        )
    }

    @Test
    fun `proxy group item maps to a drop down field state`() {
        val item =
            ConfigurationItem(
                definition(ConfigItemType.ProxyGroupConfigItem),
                value = null,
                options = listOf(ConfigValue.ProxyGroupValue(ProxyGroup("p", "Proxies", 1, emptySequence()))),
            )
        assertTrue(item.toState() is DropDownFieldState<*>)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `custom dataset group item maps to a drop down field state`() {
        val group =
            CustomDatasetGroup(
                id = "d",
                name = "Dataset",
                numberOfItems = 0,
                itemDefinitions = emptyList(),
                items = emptySequence(),
                createdAt =
                    kotlin.time.Clock.System
                        .now(),
            )
        val item =
            ConfigurationItem(
                definition(ConfigItemType.GroupedConfigItem(emptyList())),
                value = null,
                options = listOf(ConfigValue.CustomDatasetGroupValue(group)),
            )
        assertTrue(item.toState() is DropDownFieldState<*>)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `EnumItem with isNullable true creates clearable DropDownFieldState`() {
        val definition =
            ScriptConfigurationItemDefinition(
                name = "Status",
                description = "Status field",
                key = "status",
                position = 0,
                type = ConfigItemType.EnumConfigItem(Status::class as kotlin.reflect.KClass<Enum<*>>),
                isNullable = true,
                stateModifier = null,
                isScriptIdentifier = false,
            )
        val item =
            ConfigurationItem(
                definition = definition,
                value = ConfigValue.EnumValue(Status.ACTIVE),
                options = Status.entries.map { ConfigValue.EnumValue(it) },
            )

        val state =
            item.createFormFieldState(
                onValueChange = {},
                scriptConfigProvider = scriptConfigProvider,
                onImportFile = { _, _ -> },
            ) as DropDownFieldState<*>

        assertTrue(state.showClearButton())
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `EnumItem with isNullable false creates non-clearable DropDownFieldState`() {
        val definition =
            ScriptConfigurationItemDefinition(
                name = "Status",
                description = "Status field",
                key = "status",
                position = 0,
                type = ConfigItemType.EnumConfigItem(Status::class as kotlin.reflect.KClass<Enum<*>>),
                isNullable = false,
                stateModifier = null,
                isScriptIdentifier = false,
            )
        val item =
            ConfigurationItem(
                definition = definition,
                value = ConfigValue.EnumValue(Status.ACTIVE),
                options = Status.entries.map { ConfigValue.EnumValue(it) },
            )

        val state =
            item.createFormFieldState(
                onValueChange = {},
                scriptConfigProvider = scriptConfigProvider,
                onImportFile = { _, _ -> },
            ) as DropDownFieldState<*>

        assertFalse(state.showClearButton())
    }

    // region Secret

    @Test
    fun `secret item maps to a secret text field state`() {
        val state =
            ConfigurationItem(
                definition(ConfigItemType.SecretConfigItem),
                ConfigValue.SecretValue(Secret("sk-live-token")),
            ).toState()

        assertTrue(state is SecretTextFieldState)
    }

    @Test
    fun `secret item is pre-filled with the revealed credential, not the mask`() {
        val state =
            ConfigurationItem(
                definition(ConfigItemType.SecretConfigItem),
                ConfigValue.SecretValue(Secret("sk-live-token")),
            ).toState() as SecretTextFieldState

        // Reopening the configuration must not require re-entry, and must not fill the field with "***".
        assertEquals("sk-live-token", state.text)
    }

    @Test
    fun `an unset secret item starts empty`() {
        val state = ConfigurationItem(definition(ConfigItemType.SecretConfigItem), value = null).toState() as SecretTextFieldState

        assertEquals("", state.text)
    }

    @Test
    fun `a secret field validates to a Secret wrapping what was typed`() {
        val state = ConfigurationItem(definition(ConfigItemType.SecretConfigItem), value = null).toState() as SecretTextFieldState

        state.onValueChange("sk-live-token")

        assertEquals(Secret("sk-live-token"), state.getValidatedValue())
    }

    @Test
    fun `a blank secret field validates to null, matching the string rule`() {
        val state = ConfigurationItem(definition(ConfigItemType.SecretConfigItem), value = null).toState() as SecretTextFieldState

        state.onValueChange("   ")

        assertNull(state.getValidatedValue())
    }

    // endregion

    private enum class Status { ACTIVE, INACTIVE }
}

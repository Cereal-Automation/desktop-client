package com.cereal.client.presentation.tasks.script.overview.configuration.model

import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.presentation.view.fields.state.SwitchFieldState
import com.cereal.client.presentation.view.fields.state.TextFieldState
import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.StateModifier
import com.cereal.sdk.statemodifier.Visibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigurationItemsFormSectionTest {
    private fun definition(
        key: String,
        type: ConfigItemType,
        isNullable: Boolean = false,
        stateModifier: StateModifier? = null,
    ) = ScriptConfigurationItemDefinition(
        name = key,
        description = "desc",
        key = key,
        position = 0,
        type = type,
        isNullable = isNullable,
        stateModifier = stateModifier,
        isScriptIdentifier = false,
    )

    private fun section(items: List<ConfigurationItem>) = ConfigurationItemsFormSection(items) { _, _, _ -> }

    @Test
    fun `builds a form field state per configuration item`() {
        val sectionUnderTest =
            section(
                listOf(
                    ConfigurationItem(definition("s", ConfigItemType.StringConfigItem), ConfigValue.StringValue("hello")),
                    ConfigurationItem(definition("b", ConfigItemType.BooleanConfigItem), ConfigValue.BooleanValue(false)),
                ),
            )

        assertEquals(2, sectionUnderTest.getFormFieldStates().size)
    }

    @Test
    fun `collects validated values into script configuration values`() {
        val sectionUnderTest =
            section(listOf(ConfigurationItem(definition("s", ConfigItemType.StringConfigItem), ConfigValue.StringValue("hello"))))

        val values = sectionUnderTest.getScriptConfigurationValues()

        assertEquals(ConfigValue.StringValue("hello"), values["s"])
    }

    @Test
    fun `reports required items via the definition`() {
        val required =
            section(listOf(ConfigurationItem(definition("s", ConfigItemType.StringConfigItem), ConfigValue.StringValue("hello"))))
        val optional =
            section(
                listOf(
                    ConfigurationItem(
                        definition("s", ConfigItemType.StringConfigItem, isNullable = true),
                        ConfigValue.StringValue("hello"),
                    ),
                ),
            )

        assertTrue(required.containsRequiredConfigurationItems())
        assertFalse(optional.containsRequiredConfigurationItems())
    }

    @Test
    fun `applyConfigurationValues pushes values into every field type`() {
        val sectionUnderTest =
            section(
                listOf(
                    ConfigurationItem(definition("s", ConfigItemType.StringConfigItem), ConfigValue.StringValue("")),
                    ConfigurationItem(definition("b", ConfigItemType.BooleanConfigItem), ConfigValue.BooleanValue(false)),
                    ConfigurationItem(
                        definition("e", ConfigItemType.EnumConfigItem(asEnumClass()), isNullable = true),
                        value = null,
                        options = Sample.entries.map { ConfigValue.EnumValue(it) },
                    ),
                ),
            )

        sectionUnderTest.applyConfigurationValues(
            mapOf(
                "s" to ConfigValue.StringValue("updated"),
                "b" to ConfigValue.BooleanValue(true),
                "e" to ConfigValue.EnumValue(Sample.B),
            ),
        )

        val states = sectionUnderTest.configurationItemToFieldState
        val stringState = states.entries.first { it.key.definition.key == "s" }.value as TextFieldState<*>
        val switchState = states.entries.first { it.key.definition.key == "b" }.value as SwitchFieldState

        assertEquals("updated", stringState.text)
        assertTrue(switchState.isChecked)
    }

    @Test
    fun `applyStateModifiers updates visibility and required flags`() {
        val modifier =
            object : StateModifier {
                override fun getVisibility(scriptConfig: ScriptConfig): Visibility = Visibility.VisibleRequired

                override fun getError(scriptConfig: ScriptConfig): String? = null
            }
        val sectionUnderTest =
            section(
                listOf(
                    ConfigurationItem(
                        definition("s", ConfigItemType.StringConfigItem, isNullable = true, stateModifier = modifier),
                        ConfigValue.StringValue("hello"),
                    ),
                ),
            )

        sectionUnderTest.applyStateModifiers()

        val state = sectionUnderTest.getFormFieldStates().first()
        assertTrue(state.isVisible.value)
        assertTrue(state.isRequired.value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun asEnumClass() = Sample::class as kotlin.reflect.KClass<Enum<*>>

    private enum class Sample { A, B }
}

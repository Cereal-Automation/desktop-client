package com.cereal.client.presentation.view.fields.validator

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.ScriptConfigValue
import com.cereal.sdk.statemodifier.StateModifier
import com.cereal.sdk.statemodifier.Visibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.time.ExperimentalTime

/**
 * Verifies the presentation-layer field validators that wrap a [ScriptConfigurationItemDefinition].
 * The SDK [ScriptConfig] is a genuine external seam with no observable state, so it is faked with a
 * minimal in-test implementation rather than asserted against.
 */
class ScriptConfigurationItemDefinitionValidatorsTest {
    private val emptyConfig =
        object : ScriptConfig {
            override fun valueForKey(key: String): ScriptConfigValue = ScriptConfigValue.NullScriptConfigValue
        }

    private fun definition(
        type: ConfigItemType,
        isNullable: Boolean = false,
        stateModifier: StateModifier? = null,
        key: String = "key",
    ) = ScriptConfigurationItemDefinition(
        name = "Field",
        description = "A field",
        key = key,
        position = 0,
        type = type,
        isNullable = isNullable,
        stateModifier = stateModifier,
        isScriptIdentifier = false,
    )

    private fun visibilityModifier(
        visibility: Visibility,
        error: String? = null,
    ) = object : StateModifier {
        override fun getVisibility(scriptConfig: ScriptConfig): Visibility = visibility

        override fun getError(scriptConfig: ScriptConfig): String? = error
    }

    @Test
    fun `boolean validator requires a value when the field is required`() {
        val validator =
            ScriptConfigurationItemDefinitionBooleanFieldValidator(
                definition(ConfigItemType.BooleanConfigItem),
            ) { emptyConfig }

        assertNotNull(validator.validate(null))
        assertNull(validator.validate(true))
    }

    @Test
    fun `boolean validator is satisfied by a null value when nullable`() {
        val validator =
            ScriptConfigurationItemDefinitionBooleanFieldValidator(
                definition(ConfigItemType.BooleanConfigItem, isNullable = true),
            ) { emptyConfig }

        assertNull(validator.validate(null))
    }

    @Test
    fun `boolean validator surfaces a state modifier error`() {
        val validator =
            ScriptConfigurationItemDefinitionBooleanFieldValidator(
                definition(
                    ConfigItemType.BooleanConfigItem,
                    isNullable = true,
                    stateModifier = visibilityModifier(Visibility.VisibleOptional, error = "bad combination"),
                ),
            ) { emptyConfig }

        assertEquals("bad combination", validator.validate(true))
    }

    @Test
    fun `file validator requires a value when the field is required`() {
        val validator =
            ScriptConfigurationItemDefinitionFileFieldValidator(
                definition(ConfigItemType.StringConfigItem),
            ) { emptyConfig }

        assertNotNull(validator.validate(null))
        assertNull(validator.validate(File("/tmp/x")))
    }

    @Test
    fun `string validator reports the required error first`() {
        val validator =
            ScriptConfigurationItemDefinitionStringFieldValidator(
                definition(ConfigItemType.StringConfigItem),
            ) { emptyConfig }

        assertNotNull(validator.validate(null))
        assertNull(validator.validate("hello"))
    }

    @Test
    fun `string validator applies the int type validator for int items`() {
        val validator =
            ScriptConfigurationItemDefinitionStringFieldValidator(
                definition(ConfigItemType.IntConfigItem),
            ) { emptyConfig }

        assertNotNull(validator.validate("not a number"))
        assertNull(validator.validate("42"))
    }

    @Test
    fun `string validator applies the float and double type validators`() {
        val floatValidator =
            ScriptConfigurationItemDefinitionStringFieldValidator(
                definition(ConfigItemType.FloatConfigItem),
            ) { emptyConfig }
        val doubleValidator =
            ScriptConfigurationItemDefinitionStringFieldValidator(
                definition(ConfigItemType.DoubleConfigItem),
            ) { emptyConfig }

        assertNotNull(floatValidator.validate("abc"))
        assertNull(floatValidator.validate("1.5"))
        assertNotNull(doubleValidator.validate("abc"))
        assertNull(doubleValidator.validate("1.5"))
    }

    @Test
    fun `string validator has no type validator for plain string items`() {
        val validator =
            ScriptConfigurationItemDefinitionStringFieldValidator(
                definition(ConfigItemType.StringConfigItem, isNullable = true),
            ) { emptyConfig }

        // Nullable string with a non-empty value: no required error, no type error.
        assertNull(validator.validate("anything"))
    }

    @Test
    fun `custom dataset validator requires a value when required`() {
        val validator =
            ScriptConfigurationItemDefinitionCustomDatasetFieldValidator(
                definition(ConfigItemType.GroupedConfigItem(emptyList())),
            ) { emptyConfig }

        assertNotNull(validator.validate(null))
    }

    @Test
    fun `custom dataset validator rejects values that are not a dataset group`() {
        val validator =
            ScriptConfigurationItemDefinitionCustomDatasetFieldValidator(
                definition(ConfigItemType.GroupedConfigItem(emptyList())),
            ) { emptyConfig }

        assertThrows<RuntimeException> { validator.validate("not a group") }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `custom dataset validator flags a required dataset field with no value`() {
        val requiredField = definition(ConfigItemType.StringConfigItem, key = "email")
        val validator =
            ScriptConfigurationItemDefinitionCustomDatasetFieldValidator(
                definition(ConfigItemType.GroupedConfigItem(listOf(requiredField))),
            ) { emptyConfig }

        val group =
            CustomDatasetGroup(
                id = "g1",
                name = "Group",
                numberOfItems = 0,
                itemDefinitions = listOf(requiredField),
                items = emptySequence(),
                createdAt =
                    kotlin.time.Clock.System
                        .now(),
            )

        val error = validator.validate(group)

        assertNotNull(error)
        assertEquals("A dataset with 'email' in it is required.", error)
    }
}

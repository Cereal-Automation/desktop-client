package com.cereal.client.presentation.tasks.script.overview.configuration

import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigKey
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.ListRows
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.presentation.view.fields.state.DoubleTextFieldState
import com.cereal.client.presentation.view.fields.state.DropDownFieldState
import com.cereal.client.presentation.view.fields.state.FloatTextFieldState
import com.cereal.client.presentation.view.fields.state.FormFieldState
import com.cereal.client.presentation.view.fields.state.IntTextFieldState
import com.cereal.client.presentation.view.fields.state.ListFieldState
import com.cereal.client.presentation.view.fields.state.ListRowState
import com.cereal.client.presentation.view.fields.state.SecretTextFieldState
import com.cereal.client.presentation.view.fields.state.StringTextFieldState
import com.cereal.client.presentation.view.fields.state.SwitchFieldState
import com.cereal.client.presentation.view.fields.validator.ScriptConfigurationItemDefinitionAnyFieldValidator
import com.cereal.client.presentation.view.fields.validator.ScriptConfigurationItemDefinitionBooleanFieldValidator
import com.cereal.client.presentation.view.fields.validator.ScriptConfigurationItemDefinitionCustomDatasetFieldValidator
import com.cereal.client.presentation.view.fields.validator.ScriptConfigurationItemDefinitionListFieldValidator
import com.cereal.client.presentation.view.fields.validator.ScriptConfigurationItemDefinitionStringFieldValidator
import com.cereal.sdk.statemodifier.ScriptConfig

// Exhaustive when over every configuration item type; length is inherent to the mapping.
@Suppress("LongMethod")
fun ConfigurationItem.createFormFieldState(
    onValueChange: (FormFieldState<*, *>) -> Unit,
    scriptConfigProvider: () -> ScriptConfig,
    onImportFile: (configurationItem: ConfigurationItem, formFieldState: FormFieldState<*, *>) -> Unit,
): FormFieldState<*, *> =
    when (definition.type) {
        ConfigItemType.BooleanConfigItem -> {
            SwitchFieldState(
                validators =
                    listOf(
                        ScriptConfigurationItemDefinitionBooleanFieldValidator(definition, scriptConfigProvider),
                    ),
                initialValue = (value as? ConfigValue.BooleanValue)?.raw ?: false,
                initiallySet = value is ConfigValue.BooleanValue,
                onValueChange = onValueChange,
            )
        }

        is ConfigItemType.EnumConfigItem -> {
            DropDownFieldState(
                options.map { (it as ConfigValue.EnumValue).raw },
                clearable = definition.isNullable,
                validators =
                    listOf(
                        ScriptConfigurationItemDefinitionAnyFieldValidator(definition, scriptConfigProvider),
                    ),
                onValueChange = onValueChange,
                initialSelectedValue = (value as? ConfigValue.EnumValue)?.raw,
            )
        }

        ConfigItemType.IntConfigItem -> {
            IntTextFieldState(
                validators =
                    listOf(
                        ScriptConfigurationItemDefinitionStringFieldValidator(
                            definition,
                            scriptConfigProvider,
                        ),
                    ),
                initialValue = (value as? ConfigValue.IntValue)?.raw,
                onValueChange = onValueChange,
            )
        }

        ConfigItemType.FloatConfigItem -> {
            FloatTextFieldState(
                validators =
                    listOf(
                        ScriptConfigurationItemDefinitionStringFieldValidator(
                            definition,
                            scriptConfigProvider,
                        ),
                    ),
                initialValue = (value as? ConfigValue.FloatValue)?.raw,
                onValueChange = onValueChange,
            )
        }

        ConfigItemType.DoubleConfigItem -> {
            DoubleTextFieldState(
                validators =
                    listOf(
                        ScriptConfigurationItemDefinitionStringFieldValidator(
                            definition,
                            scriptConfigProvider,
                        ),
                    ),
                initialValue = (value as? ConfigValue.DoubleValue)?.raw,
                onValueChange = onValueChange,
            )
        }

        ConfigItemType.StringConfigItem -> {
            StringTextFieldState(
                validators =
                    listOf(
                        ScriptConfigurationItemDefinitionStringFieldValidator(
                            definition,
                            scriptConfigProvider,
                        ),
                    ),
                initialValue = (value as? ConfigValue.StringValue)?.raw ?: "",
                onValueChange = onValueChange,
            )
        }

        ConfigItemType.SecretConfigItem -> {
            SecretTextFieldState(
                validators =
                    listOf(
                        ScriptConfigurationItemDefinitionStringFieldValidator(
                            definition,
                            scriptConfigProvider,
                        ),
                    ),
                // Pre-filled with the revealed credential so reopening the configuration does not
                // require re-entry; the field still renders masked.
                initialValue = (value as? ConfigValue.SecretValue)?.raw?.reveal() ?: "",
                onValueChange = onValueChange,
            )
        }

        is ConfigItemType.GroupedConfigItem -> {
            DropDownFieldState(
                options.map { (it as ConfigValue.CustomDatasetGroupValue).raw },
                validators =
                    listOf(
                        ScriptConfigurationItemDefinitionCustomDatasetFieldValidator(definition, scriptConfigProvider),
                    ),
                onValueChange = onValueChange,
                onImport = { onImportFile(this, it) },
                initialSelectedValue = (value as? ConfigValue.CustomDatasetGroupValue)?.raw,
            )
        }

        ConfigItemType.ProxyConfigItem, ConfigItemType.ProxyGroupConfigItem -> {
            DropDownFieldState(
                options.map { (it as ConfigValue.ProxyGroupValue).raw },
                validators =
                    listOf(
                        ScriptConfigurationItemDefinitionAnyFieldValidator(definition, scriptConfigProvider),
                    ),
                onValueChange = onValueChange,
                onImport = { onImportFile(this, it) },
                initialSelectedValue = (value as? ConfigValue.ProxyGroupValue)?.raw,
            )
        }

        is ConfigItemType.ListConfigItem -> {
            val fieldDefinitions = definition.type.items
            ListFieldState(
                fieldDefinitions = fieldDefinitions,
                createRow = { initialFields, onRowValueChange ->
                    createListRow(fieldDefinitions, initialFields, onRowValueChange)
                },
                validators =
                    listOf(
                        ScriptConfigurationItemDefinitionListFieldValidator(definition, scriptConfigProvider),
                    ),
                initialValue = (value as? ConfigValue.ListValue)?.raw ?: ListRows.EMPTY,
                onValueChange = { onValueChange(it) },
                onImport = { onImportFile(this, it) },
            )
        }
    }

/**
 * Builds the form field states for one row of a list, reusing [createFormFieldState] so a record
 * field gets exactly the widget and validator its type already has at top level.
 *
 * Each field is given a [ScriptConfig] view of its *own* row, so a field's required-ness and any error it
 * reports are evaluated against the row it belongs to rather than the whole script configuration.
 */
private fun createListRow(
    fieldDefinitions: List<ScriptConfigurationItemDefinition>,
    initialFields: Map<ConfigKey, ConfigValue>,
    onRowValueChange: () -> Unit,
): ListRowState {
    val row = ListRowState(fieldDefinitions, onRowValueChange)
    fieldDefinitions.forEach { fieldDefinition ->
        val fieldState =
            ConfigurationItem(
                definition = fieldDefinition,
                value = initialFields[fieldDefinition.key],
                options = fieldDefinition.listFieldOptions(),
            ).createFormFieldState(
                onValueChange = { row.onFieldValueChanged() },
                scriptConfigProvider = { RawScriptConfigValues(row.fields()) },
                onImportFile = { _, _ ->
                    throw UnsupportedOperationException("Importing from a file is not supported inside a list row.")
                },
            )
        row.putFieldState(fieldDefinition.key, fieldState)
    }
    return row
}

/**
 * The selectable options for a record field. Only enum fields have any; the remaining permitted field
 * types are free input.
 */
private fun ScriptConfigurationItemDefinition.listFieldOptions(): List<ConfigValue> =
    (type as? ConfigItemType.EnumConfigItem)
        ?.enumType
        ?.java
        ?.enumConstants
        ?.map { ConfigValue.EnumValue(it) }
        .orEmpty()

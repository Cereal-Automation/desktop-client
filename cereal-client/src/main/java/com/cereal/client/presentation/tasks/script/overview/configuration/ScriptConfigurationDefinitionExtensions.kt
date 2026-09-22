package com.cereal.client.presentation.tasks.script.overview.configuration

import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.configuration.ApplicationScriptConfigurationKeys
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.compareTo
import com.cereal.client.domain.model.script.configuration.containsValidData
import com.cereal.client.domain.model.script.configuration.groupedConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.isValidReturnType
import com.cereal.client.presentation.view.fields.validator.isRequired
import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.ScriptConfigValue

fun ScriptConfigurationDefinition.isValid(
    values: ScriptConfigurationValues,
    isTaskConfiguration: Boolean = false,
): Boolean = this.validate(values, isTaskConfiguration).isEmpty()

fun ScriptConfigurationDefinition.validate(
    values: ScriptConfigurationValues,
    isTaskConfiguration: Boolean = false,
): List<ScriptConfigurationItemDefinition> {
    val invalidItems = mutableListOf<ScriptConfigurationItemDefinition>()
    val scriptConfigValues = RawScriptConfigValues(values)

    // Validate types
    this.configurationItems.forEach {
        val value = values[it.key]

        if (it.isRequired(scriptConfigValues) && value == null && it.defaultValue == null) {
            // Required but no value provided and no default value available
            invalidItems.add(it)
        } else if (value != null &&
            (
                !it.isValidReturnType(
                    value,
                    isTaskConfiguration,
                ) ||
                    !it.containsValidData(value)
            )
        ) {
            // Value provided but invalid return type or invalid data.
            invalidItems.add(it)
        } else if (!isTaskConfiguration && it.stateModifier?.getError(scriptConfigValues) != null) { // Because state modifiers work on script level we can't use them when validating a configuration for a task
            // State modifier error.
            invalidItems.add(it)
        }
    }

    // Extra validation if a custom dataset group is provided.
    (values[ApplicationScriptConfigurationKeys.KEY_CUSTOM_DATASET.key] as? ConfigValue.CustomDatasetGroupValue)?.raw?.let {
        val groupedConfigItems =
            (this.groupedConfigurationDefinition()!!.type as ConfigItemType.GroupedConfigItem).items
        // Check custom dataset items against the required definition.
        invalidItems.addAll(groupedConfigItems.compareTo(it.itemDefinitions))

        // Check custom dataset items against the actual values.
        invalidItems.addAll(groupedConfigItems.containsValidData(scriptConfigValues))
    }

    // Extra validation if a custom dataset item is provided.
    (values[ApplicationScriptConfigurationKeys.KEY_CUSTOM_DATASET.key] as? ConfigValue.CustomDatasetItemValue)?.raw?.let {
        val groupedConfigItems =
            (this.groupedConfigurationDefinition()!!.type as ConfigItemType.GroupedConfigItem).items
        // Check custom dataset items against the actual values.
        invalidItems.addAll(
            groupedConfigItems.containsValidData(
                RawScriptConfigValues(it.fields.filterValues { it != null }.mapValues { (_, v) -> v!! }),
            ),
        )
    }

    return invalidItems
}

/**
 * Checks each item in the list of `ScriptConfigurationItemDefinition` to determine if it has valid data
 * based on the provided `ScriptConfig`. Returns a list of invalid item definitions.
 *
 * @param scriptConfig The configuration containing the values the user has provided for the script.
 * @return A list of `ScriptConfigurationItemDefinition` objects that do not contain valid data.
 */
private fun List<ScriptConfigurationItemDefinition>.containsValidData(scriptConfig: ScriptConfig): List<ScriptConfigurationItemDefinition> {
    val invalidItems = mutableListOf<ScriptConfigurationItemDefinition>()

    this.forEach { definition ->
        if (definition.isRequired(scriptConfig)) {
            val fieldValue = scriptConfig.valueForKey(definition.key)
            when {
                fieldValue == ScriptConfigValue.NullScriptConfigValue && definition.defaultValue == null -> {
                    invalidItems.add(definition)
                }

                fieldValue is ScriptConfigValue.SequenceScriptConfigValue &&
                    fieldValue.values.any { it == ScriptConfigValue.NullScriptConfigValue } &&
                    definition.defaultValue == null -> {
                    invalidItems.add(definition)
                }
            }
        }

        definition.stateModifier?.getError(scriptConfig)?.let { _ ->
            invalidItems.add(definition)
        }
    }

    return invalidItems
}

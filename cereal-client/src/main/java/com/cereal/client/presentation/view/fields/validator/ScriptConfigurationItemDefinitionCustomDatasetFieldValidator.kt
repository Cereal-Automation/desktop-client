package com.cereal.client.presentation.view.fields.validator

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.ScriptConfigValue

class ScriptConfigurationItemDefinitionCustomDatasetFieldValidator(
    private val definition: ScriptConfigurationItemDefinition,
    private val scriptConfigProvider: () -> ScriptConfig,
) : AnyFieldValidator {
    override fun validate(value: Any?): String? {
        val scriptConfig = scriptConfigProvider()

        if (definition.isRequired(scriptConfig) && value == null) {
            return "A value is required"
        }

        if (value != null) {
            if (value !is CustomDatasetGroup) {
                throw RuntimeException("This validator can only be used on values of type CustomDatasetGroup.")
            }

            validateDatasetItems(scriptConfig)?.let { return it }
        }

        return definition.stateModifier?.getError(scriptConfig)
    }

    private fun validateDatasetItems(scriptConfig: ScriptConfig): String? {
        val datasetItemDefinitions = (definition.type as ConfigItemType.GroupedConfigItem).items

        // Check required fields inside the dataset.
        datasetItemDefinitions.forEach { itemDefinition ->
            validateDatasetItem(itemDefinition, scriptConfig)?.let { return it }
        }
        return null
    }

    private fun validateDatasetItem(
        itemDefinition: ScriptConfigurationItemDefinition,
        scriptConfig: ScriptConfig,
    ): String? {
        if (itemDefinition.isRequired(scriptConfig)) {
            val fieldValue = scriptConfig.valueForKey(itemDefinition.key)
            when {
                fieldValue == ScriptConfigValue.NullScriptConfigValue -> {
                    return "A dataset with '${itemDefinition.key}' in it is required."
                }

                fieldValue is ScriptConfigValue.SequenceScriptConfigValue &&
                    fieldValue.values.any { it == ScriptConfigValue.NullScriptConfigValue } -> {
                    return "'${itemDefinition.key}' is required but contains empty values."
                }
            }
        }

        itemDefinition.stateModifier?.getError(scriptConfig)?.let { error ->
            return "'${itemDefinition.key}' contains an invalid value: $error"
        }
        return null
    }
}

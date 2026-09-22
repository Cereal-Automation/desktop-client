package com.cereal.client.presentation.tasks.script.overview.configuration

import com.cereal.client.application.task.toComponentSecret
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.configuration.ApplicationScriptConfigurationKeys
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.ScriptConfigValue
import com.cereal.sdk.models.proxy.ProxyGroup as SdkProxyGroup

class RawScriptConfigValues(
    private val values: ScriptConfigurationValues,
) : ScriptConfig {
    override fun valueForKey(key: String): ScriptConfigValue =
        values[key]?.let {
            convertToScriptConfigValue(it)
        } ?: run {
            val datasetGroup =
                (values[ApplicationScriptConfigurationKeys.KEY_CUSTOM_DATASET.key] as? ConfigValue.CustomDatasetGroupValue)?.raw

            // Try to find the key in the definitions of the custom dataset. If so return a SequenceScriptConfigValue with
            // all the values available for that key.
            if (datasetGroup?.itemDefinitions?.any { it.key == key } == true) {
                val sequenceOfValues =
                    datasetGroup.items
                        .map { item -> item.fields[key] }
                        .map { value -> if (value != null) convertToScriptConfigValue(value) else ScriptConfigValue.NullScriptConfigValue }
                ScriptConfigValue.SequenceScriptConfigValue(sequenceOfValues)
            } else {
                ScriptConfigValue.NullScriptConfigValue
            }
        }

    /**
     * Converts a [ConfigValue] into a corresponding `ScriptConfigValue` type.
     *
     * @param value The configuration value to be converted.
     * @return The `ScriptConfigValue` type corresponding to the input value's type.
     * @throws RuntimeException if an unsupported type is encountered.
     */
    private fun convertToScriptConfigValue(value: ConfigValue): ScriptConfigValue =
        when (value) {
            is ConfigValue.DoubleValue -> {
                ScriptConfigValue.DoubleScriptConfigValue(value.raw)
            }

            is ConfigValue.IntValue -> {
                ScriptConfigValue.IntScriptConfigValue(value.raw)
            }

            is ConfigValue.BooleanValue -> {
                ScriptConfigValue.BooleanScriptConfigValue(value.raw)
            }

            is ConfigValue.StringValue -> {
                ScriptConfigValue.StringScriptConfigValue(value.raw)
            }

            // Not optional: this conversion throws on unrecognised types and runs on every keystroke
            // through the required-field check, so without this branch the configuration screen
            // crashes the moment a secret field exists.
            is ConfigValue.SecretValue -> {
                ScriptConfigValue.SecretScriptConfigValue(value.raw.toComponentSecret())
            }

            is ConfigValue.FloatValue -> {
                ScriptConfigValue.FloatScriptConfigValue(value.raw)
            }

            is ConfigValue.ProxyGroupValue -> {
                ScriptConfigValue.ProxyGroupScriptConfigValue(
                    SdkProxyGroup(
                        value.raw.id,
                        value.raw.name,
                        value.raw.numberOfItems,
                    ),
                )
            }

            is ConfigValue.ListValue -> {
                // Each row is exposed as its own configuration view, so a state modifier reads a row's
                // field with the same valueForKey call it uses for top-level items.
                ScriptConfigValue.ListScriptConfigValue(
                    value.raw.rows.map { row -> RawScriptConfigValues(row.fields) },
                )
            }

            is ConfigValue.EnumValue -> {
                ScriptConfigValue.EnumScriptConfigValue(value.raw)
            }

            else -> {
                throw RuntimeException("Unsupported type found ${value::class.simpleName}")
            }
        }
}

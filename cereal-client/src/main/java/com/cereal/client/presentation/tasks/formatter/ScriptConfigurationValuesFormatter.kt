package com.cereal.client.presentation.tasks.formatter

import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.configuration.ApplicationScriptConfigurationKeys
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.itemForKey

/**
 * Creates a string representation of the script configuration values.
 *
 * @param scriptConfig The definition containing script configuration items.
 * @param compact When true, includes the keys in the output. When false, only the values are output.
 * @param separator The string used to separate key-value pairs in the output string.
 * @return A string representation of the script configuration values.
 */
fun ScriptConfigurationValues.joinToString(
    scriptConfig: ScriptConfigurationDefinition,
    compact: Boolean = false,
    separator: String = " - ",
): String {
    val formattedValues =
        mapNotNull { (key, value) ->
            val commonName = scriptConfig.itemForKey(key)?.name ?: key
            when (value) {
                is ConfigValue.ProxyValue -> {
                    val proxy = value.raw
                    commonName to "${proxy.address}:${proxy.port}"
                }

                // CustomDatasetItem are added separately.
                is ConfigValue.CustomDatasetItemValue -> {
                    null
                }

                // A list would otherwise render as its internal data class representation.
                is ConfigValue.ListValue -> {
                    commonName to value.raw.describe()
                }

                else -> {
                    commonName to value.raw
                }
            }
        }.toMap()

    val customDatasetKey =
        ApplicationScriptConfigurationKeys.KEY_CUSTOM_DATASET.key
    val customData = (this[customDatasetKey] as? ConfigValue.CustomDatasetItemValue)?.raw
    val result =
        if (customData != null) {
            val customFields =
                customData.fields
                    .map { (key, value) ->
                        val fieldName = scriptConfig.itemForKey(key)?.name ?: key
                        fieldName to value?.raw.toString()
                    }.toMap()
            formattedValues + customFields
        } else {
            formattedValues
        }

    return result.entries
        .joinToString(separator) {
            if (compact) {
                it.value.toString()
            } else {
                "${it.key}: ${it.value}"
            }
        }
}

package com.cereal.client.domain.model.script.configuration

import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.sdk.ScriptConfiguration
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * @param configurationItems the script configuration definitions.
 */
data class ScriptConfigurationDefinition(
    val scriptConfigurationClass: KClass<ScriptConfiguration>,
    val configurationItems: List<ScriptConfigurationItemDefinition>,
)

fun ScriptConfigurationDefinition.itemForKey(key: String): ScriptConfigurationItemDefinition? =
    configurationItems.firstOrNull {
        it.key == key
    }

fun ScriptConfigurationDefinition.proxyConfigurationDefinition(): ScriptConfigurationItemDefinition? =
    configurationItems.firstOrNull {
        it.type is ConfigItemType.ProxyConfigItem
    }

fun ScriptConfigurationDefinition.getScriptIdentifierValue(values: ScriptConfigurationValues): String? = this.configurationItems.firstOrNull { it.isScriptIdentifier }?.getValue(values)

fun ScriptConfigurationDefinition.groupedConfigurationDefinition(): ScriptConfigurationItemDefinition? =
    configurationItems.firstOrNull {
        it.type is ConfigItemType.GroupedConfigItem
    }

/**
 * Compares the current ScriptConfigurationItemDefinition with a list of expected definitions and identifies invalid items.
 * An item is considered invalid if:
 * - The item is not found in the expected definitions and it's not nullable.
 * - The item types do not match and the current type is not a subclass of the expected type.
 *
 * @param expectedDefinitions The list of expected ScriptConfigurationItemDefinition to compare against.
 * @return A list of ScriptConfigurationItemDefinition that do not match the expected definitions.
 */
fun List<ScriptConfigurationItemDefinition>.compareTo(
    expectedDefinitions: List<ScriptConfigurationItemDefinition>,
): List<ScriptConfigurationItemDefinition> {
    val invalidItems = mutableListOf<ScriptConfigurationItemDefinition>()

    this.forEach { expectedDefinition ->
        val selectedDefinition = expectedDefinitions.find { it.key == expectedDefinition.key }
        if (selectedDefinition == null && !expectedDefinition.isNullable) {
            invalidItems.add(expectedDefinition)
        } else if (selectedDefinition != null &&
            expectedDefinition.type != selectedDefinition.type &&
            !expectedDefinition.type.scriptValueType.isSubclassOf(
                selectedDefinition.type.scriptValueType,
            )
        ) {
            invalidItems.add(expectedDefinition)
        }
    }

    return invalidItems
}

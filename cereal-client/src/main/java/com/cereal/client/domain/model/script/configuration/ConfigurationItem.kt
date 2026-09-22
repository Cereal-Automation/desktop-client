package com.cereal.client.domain.model.script.configuration

/**
 * Pairs a configuration item's [definition] with its currently-configured [value] (null when unset).
 *
 * This is a thin definition + [ConfigValue] pairing: the item's variant (boolean, string, enum, proxy
 * group, …) is carried by [ScriptConfigurationItemDefinition.type], and its value by the canonical
 * [ConfigValue] model — there is no parallel per-type subclass hierarchy.
 *
 * @property options for choice-based items (enum / proxy group / custom dataset group), the values the
 *   user can select from; empty for all other types.
 */
data class ConfigurationItem(
    val definition: ScriptConfigurationItemDefinition,
    val value: ConfigValue?,
    val options: List<ConfigValue> = emptyList(),
)

package com.cereal.client.domain.model.agent

import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.itemForKey

/** Package name of the AI browser agent marketplace script. */
const val AI_BROWSER_AGENT_PACKAGE = "com.cereal.aibrowseragent"

/**
 * What the Home goal composer edits: the only four config keys of the AI browser agent it owns.
 * Every other item keeps the script's own default.
 */
data class GoalRunInput(
    val goal: String,
    val startUrl: String,
    val repeat: Boolean,
    val repeatIntervalSeconds: Int,
) {
    companion object {
        const val KEY_GOAL = "goal"
        const val KEY_START_URL = "startUrl"
        const val KEY_RUN_MODE = "runMode"
        const val KEY_REPEAT_INTERVAL = "repeatInterval"
        private const val ONCE = "Once"
        private const val REPEAT = "Repeat"

        val KEYS = listOf(KEY_GOAL, KEY_START_URL, KEY_RUN_MODE, KEY_REPEAT_INTERVAL)
    }

    /**
     * The configuration values this input maps to in [definition]. A key the definition lacks, or
     * declares with another type, is left out, so the result also works as a best-effort prefill.
     */
    fun toConfiguration(definition: ScriptConfigurationDefinition): ScriptConfigurationValues =
        buildMap {
            if (definition.hasType(KEY_GOAL, ConfigItemType.StringConfigItem)) put(KEY_GOAL, ConfigValue.StringValue(goal))
            if (definition.hasType(KEY_START_URL, ConfigItemType.StringConfigItem)) put(KEY_START_URL, ConfigValue.StringValue(startUrl))
            definition.runModeConstant(if (repeat) REPEAT else ONCE)?.let { put(KEY_RUN_MODE, ConfigValue.EnumValue(it)) }
            if (definition.hasType(KEY_REPEAT_INTERVAL, ConfigItemType.IntConfigItem)) {
                put(KEY_REPEAT_INTERVAL, ConfigValue.IntValue(repeatIntervalSeconds))
            }
        }
}

/** Whether the installed agent still declares all four composer keys with the types the composer writes. */
fun ScriptConfigurationDefinition.supportsGoalComposer(): Boolean =
    hasType(GoalRunInput.KEY_GOAL, ConfigItemType.StringConfigItem) &&
        hasType(GoalRunInput.KEY_START_URL, ConfigItemType.StringConfigItem) &&
        runModeConstant("Once") != null &&
        runModeConstant("Repeat") != null &&
        hasType(GoalRunInput.KEY_REPEAT_INTERVAL, ConfigItemType.IntConfigItem)

private fun ScriptConfigurationDefinition.hasType(
    key: String,
    type: ConfigItemType,
): Boolean = itemForKey(key)?.type == type

/** The script's own `runMode` enum constant named [name]; the client can't reference the enum class itself. */
private fun ScriptConfigurationDefinition.runModeConstant(name: String): Enum<*>? =
    (itemForKey(GoalRunInput.KEY_RUN_MODE)?.type as? ConfigItemType.EnumConfigItem)
        ?.enumType
        ?.java
        ?.enumConstants
        ?.firstOrNull { it.name == name }

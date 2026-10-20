package com.cereal.client.application.agent

import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.itemForKey
import com.cereal.client.domain.model.task.Task

/** Package name of the AI browser agent marketplace script. */
const val AI_BROWSER_AGENT_PACKAGE = "com.cereal.aibrowseragent"

/** Whether this is the AI browser agent, the script Home's goal composer starts. */
val ScriptPackage.isAiBrowserAgent: Boolean
    get() = manifest.packageName == AI_BROWSER_AGENT_PACKAGE

/** Whether this marketplace listing is the AI browser agent. */
val MarketplaceScript.isAiBrowserAgent: Boolean
    get() = publicIdentifier == AI_BROWSER_AGENT_PACKAGE

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
        internal const val ONCE = "Once"
        internal const val REPEAT = "Repeat"

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
        runModeConstant(GoalRunInput.ONCE) != null &&
        runModeConstant(GoalRunInput.REPEAT) != null &&
        hasType(GoalRunInput.KEY_REPEAT_INTERVAL, ConfigItemType.IntConfigItem)

/** A goal run read back from its task: what it was started with, and the latest cost it reported. */
data class GoalRunDetails(
    val goal: String,
    val startUrl: String,
    /** The run mode constant's name ("Once"/"Repeat"); empty if unset. */
    val mode: String,
    /** Latest "Cost: $x" the agent reported in a status message; null until it reports one. */
    val cost: String?,
)

fun Task.goalRunDetails(): GoalRunDetails {
    val config = scriptInstance.packageInstance.mainConfiguration
    return GoalRunDetails(
        goal = (config[GoalRunInput.KEY_GOAL] as? ConfigValue.StringValue)?.raw.orEmpty(),
        startUrl = (config[GoalRunInput.KEY_START_URL] as? ConfigValue.StringValue)?.raw.orEmpty(),
        mode = (config[GoalRunInput.KEY_RUN_MODE] as? ConfigValue.EnumValue)?.raw?.name.orEmpty(),
        cost = statusHistory.asReversed().firstNotNullOfOrNull { s -> s.message?.let { COST.find(it)?.groupValues?.get(1) } },
    )
}

// ponytail: the agent has no structured cost; parse its "Cost: $x" status text until it reports one.
private val COST = Regex("""Cost: (\$\d+(?:\.\d+)?)""")

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

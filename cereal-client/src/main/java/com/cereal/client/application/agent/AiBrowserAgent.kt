package com.cereal.client.application.agent

import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.task.Task

/** Package name of the AI browser agent marketplace script. */
const val AI_BROWSER_AGENT_PACKAGE = "com.cereal.aibrowseragent"

/** Whether this is the AI browser agent, the script Home's goal composer starts. */
val ScriptPackage.isAiBrowserAgent: Boolean
    get() = manifest.packageName == AI_BROWSER_AGENT_PACKAGE

/** Uninstalling removes the whole jar, so the AI browser agent package can't be uninstalled. */
val ScriptPackage.canUninstall: Boolean
    get() = !isAiBrowserAgent

/** Whether this marketplace listing is the AI browser agent. */
val MarketplaceScript.isAiBrowserAgent: Boolean
    get() = publicIdentifier == AI_BROWSER_AGENT_PACKAGE

// The agent's config keys a goal run is read back by; keyNames the script never changes.
private const val KEY_GOAL = "goal"
private const val KEY_START_URL = "startUrl"
private const val KEY_RUN_MODE = "runMode"

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
        goal = (config[KEY_GOAL] as? ConfigValue.StringValue)?.raw.orEmpty(),
        startUrl = (config[KEY_START_URL] as? ConfigValue.StringValue)?.raw.orEmpty(),
        mode = (config[KEY_RUN_MODE] as? ConfigValue.EnumValue)?.raw?.name.orEmpty(),
        cost = statusHistory.asReversed().firstNotNullOfOrNull { s -> s.message?.let { COST.find(it)?.groupValues?.get(1) } },
    )
}

// ponytail: the agent has no structured cost; parse its "Cost: $x" status text until it reports one.
private val COST = Regex("""Cost: (\$\d+(?:\.\d+)?)""")

package com.cereal.client.domain.model.script

import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.isGroup
import com.cereal.sdk.component.script.ScriptParameters
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

typealias ScriptConfigurationValues = Map<String, ConfigValue>

/**
 * @param mainConfiguration acts as template for creating [MainScriptInstance]. Also for grouped datasets this
 * configuration contains the group while the specific script instance configuration contains the specific item from that group.
 * @param childConfigurations acts as template for creating [ChildScriptInstance]. Also for grouped datasets this
 * configuration contains the group while the specific script instance configuration contains the specific item from that group.
 * @param notificationOverrides optional notification channel setting overrides for this script instance.
 * When set, these settings take precedence over global notification settings when the script sends notifications.
 */
data class ScriptPackageInstance
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: String,
        val mainConfiguration: ScriptConfigurationValues,
        val childConfigurations: Map<String, ScriptConfigurationValues>,
        val definition: ScriptPackage,
        val createdAt: Instant,
        val numberOfConcurrentTasks: Int,
        val notificationOverrides: ScriptNotificationOverrides? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ScriptPackageInstance) return false

            return id == other.id
        }

        override fun hashCode(): Int = id.hashCode()
    }

interface ScriptInstance {
    val id: String
    val definition: Script

    @OptIn(ExperimentalTime::class)
    val createdAt: Instant
    val configuration: ScriptConfigurationValues
    val packageInstance: ScriptPackageInstance
}

data class MainScriptInstance
    @OptIn(ExperimentalTime::class)
    constructor(
        override val id: String,
        override val definition: MainScript,
        override val configuration: ScriptConfigurationValues,
        override val createdAt: Instant,
        override val packageInstance: ScriptPackageInstance,
    ) : ScriptInstance

data class ChildScriptInstance
    @OptIn(ExperimentalTime::class)
    constructor(
        override val id: String,
        override val definition: ChildScript,
        override val configuration: ScriptConfigurationValues,
        override val createdAt: Instant,
        override val packageInstance: ScriptPackageInstance,
        val parent: ScriptInstance,
        val params: ScriptParameters?,
    ) : ScriptInstance

/**
 * Get the preferred number of tasks depending on if the script instance requires multiple tasks to be created. If
 * that's the case use the user provided number of tasks or else use 1.
 */
fun ScriptInstance.getNumberOfConcurrentTasks(): Int =
    if (configuration.values.any { it.isGroup }) {
        this.packageInstance.numberOfConcurrentTasks
    } else {
        1
    }

fun ScriptInstance.getScriptPackageInstance(): ScriptPackageInstance {
    var script = this
    while (script !is MainScriptInstance) {
        script = (script as ChildScriptInstance).parent
    }

    return script.packageInstance
}

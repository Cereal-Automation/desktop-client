package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.StateModifier
import com.cereal.sdk.statemodifier.Visibility

class TaskDataStateModifier(
    private val childConfigurationItemDefinitions: List<ScriptConfigurationItemDefinition>,
) : StateModifier {
    override fun getVisibility(scriptConfig: ScriptConfig): Visibility =
        if (childConfigurationItemDefinitions.any { it.stateModifier?.getVisibility(scriptConfig) == Visibility.VisibleRequired }) {
            Visibility.VisibleRequired
        } else if (childConfigurationItemDefinitions.any {
                it.stateModifier?.getVisibility(
                    scriptConfig,
                ) == Visibility.VisibleOptional
            }
        ) {
            Visibility.VisibleOptional
        } else {
            Visibility.Hidden
        }

    override fun getError(scriptConfig: ScriptConfig): String? = null
}

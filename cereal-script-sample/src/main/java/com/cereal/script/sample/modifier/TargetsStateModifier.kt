package com.cereal.script.sample.modifier

import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.ScriptConfigValue
import com.cereal.sdk.statemodifier.StateModifier
import com.cereal.sdk.statemodifier.Visibility

/**
 * Shows how a complex list's own state modifier reads the rows the user entered — here to cap the list,
 * since a maximum row count is expressed through the modifier rather than an annotation parameter.
 */
object TargetsStateModifier : StateModifier {
    private const val MAX_TARGETS = 3

    override fun getVisibility(scriptConfig: ScriptConfig): Visibility = Visibility.VisibleOptional

    override fun getError(scriptConfig: ScriptConfig): String? {
        val rows =
            (scriptConfig.valueForKey("Targets") as? ScriptConfigValue.ListScriptConfigValue)
                ?.items
                .orEmpty()

        return if (rows.size > MAX_TARGETS) "At most $MAX_TARGETS targets are supported." else null
    }
}

package com.cereal.script.sample.modifier

import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.ScriptConfigValue
import com.cereal.sdk.statemodifier.StateModifier
import com.cereal.sdk.statemodifier.Visibility

object BooleanKeyStateModifier : StateModifier {
    override fun getVisibility(scriptConfig: ScriptConfig): Visibility = Visibility.VisibleRequired

    override fun getError(scriptConfig: ScriptConfig): String? {
        val configItem = scriptConfig.valueForKey("BooleanKey") as? ScriptConfigValue.BooleanScriptConfigValue

        return if (configItem == null || !configItem.value) {
            "You must switch this boolean."
        } else {
            null
        }
    }
}

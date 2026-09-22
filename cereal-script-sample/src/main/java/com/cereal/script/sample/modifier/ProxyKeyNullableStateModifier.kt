package com.cereal.script.sample.modifier

import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.StateModifier
import com.cereal.sdk.statemodifier.Visibility

object ProxyKeyNullableStateModifier : StateModifier {
    override fun getVisibility(scriptConfig: ScriptConfig): Visibility = Visibility.VisibleOptional

    override fun getError(scriptConfig: ScriptConfig): String? = null
}

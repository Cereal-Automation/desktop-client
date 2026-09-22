package com.cereal.script.sample.modifier

import com.cereal.script.sample.SampleConfigWebsite
import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.ScriptConfigValue
import com.cereal.sdk.statemodifier.StateModifier
import com.cereal.sdk.statemodifier.Visibility

object WebsiteOptionOneStateModifier : StateModifier {
    override fun getVisibility(scriptConfig: ScriptConfig): Visibility =
        if ((scriptConfig.valueForKey("websiteKey") as? ScriptConfigValue.EnumScriptConfigValue)?.value ==
            SampleConfigWebsite.FIRST_OPTION
        ) {
            Visibility.VisibleRequired
        } else {
            Visibility.Hidden
        }

    override fun getError(scriptConfig: ScriptConfig): String? = null
}

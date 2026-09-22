package com.cereal.script.sample.child

import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.ScriptConfigurationItem

interface TestChildConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(
        keyName = "StringKey",
        name = "Child KeyOfString",
        description = "A very long long looooong description text which should describe the function of this configuration",
        position = 1,
    )
    fun keyString(): String = "default"
}

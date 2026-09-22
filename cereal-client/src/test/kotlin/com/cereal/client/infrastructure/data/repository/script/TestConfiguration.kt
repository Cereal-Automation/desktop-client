package com.cereal.client.infrastructure.data.repository.script

import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.ScriptConfigurationItem

interface TestConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(
        keyName = "key",
        name = "Key Name",
        description = "value",
    )
    fun key(): String = "default"

    @ScriptConfigurationItem(
        keyName = "key",
        name = "Key Name",
        description = "value",
    )
    fun key(key: String)

    @ScriptConfigurationItem(
        keyName = "nullDefaultKey",
        name = "Key Name",
        description = "value",
    )
    fun nullDefaultKey(key: String)

    @ScriptConfigurationItem(
        keyName = "nullDefaultKey",
        name = "Key Name",
        description = "value",
    )
    fun nullDefaultKey(): String? = null
}

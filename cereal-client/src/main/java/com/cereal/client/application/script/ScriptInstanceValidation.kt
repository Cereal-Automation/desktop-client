package com.cereal.client.application.script

import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.presentation.tasks.script.overview.configuration.isValid

fun ScriptInstance.hasValidConfiguration(): Boolean = definition.configuration.isValid(configuration)

fun ScriptPackageInstance.hasValidConfiguration(): Boolean =
    definition.mainScript.configuration.isValid(mainConfiguration) &&
        definition.childScripts.all {
            childConfigurations.containsKey(it.key) &&
                it.value.configuration.isValid(
                    childConfigurations.get(
                        key = it.key,
                    )!!,
                )
        }

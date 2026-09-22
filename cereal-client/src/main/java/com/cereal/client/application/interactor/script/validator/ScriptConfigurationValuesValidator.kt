package com.cereal.client.application.interactor.script.validator

import com.cereal.client.domain.model.exception.InvalidScriptConfigurationException
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.presentation.tasks.script.overview.configuration.isValid

class ScriptConfigurationValuesValidator(
    private val scriptPackage: ScriptPackage,
    private val mainScriptConfiguration: ScriptConfigurationValues,
    private val childConfigurations: Map<String, ScriptConfigurationValues>,
) {
    fun validate() {
        if (!scriptPackage.mainScript.configuration.isValid(mainScriptConfiguration)) {
            throw InvalidScriptConfigurationException()
        }

        scriptPackage.childScripts.all { entry ->
            childConfigurations.get(key = entry.key)?.let {
                entry.value.configuration.isValid(it)
            } ?: throw InvalidScriptConfigurationException()
        }
    }
}

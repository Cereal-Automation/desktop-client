package com.cereal.client.application.interactor.script.validator

import com.cereal.client.domain.model.exception.InvalidScriptConfigurationException
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.entry
import com.cereal.client.presentation.tasks.script.overview.configuration.isValid

class ScriptConfigurationValuesValidator(
    private val scriptPackage: ScriptPackage,
    private val mainScriptConfiguration: ScriptConfigurationValues,
    private val childConfigurations: Map<String, ScriptConfigurationValues>,
    /** The entry whose configuration is checked; null is the default entry. */
    private val variantId: String? = null,
) {
    fun validate() {
        if (!scriptPackage.entry(variantId).configuration.isValid(mainScriptConfiguration)) {
            throw InvalidScriptConfigurationException()
        }

        scriptPackage.childScripts.all { entry ->
            childConfigurations.get(key = entry.key)?.let {
                entry.value.configuration.isValid(it)
            } ?: throw InvalidScriptConfigurationException()
        }
    }
}

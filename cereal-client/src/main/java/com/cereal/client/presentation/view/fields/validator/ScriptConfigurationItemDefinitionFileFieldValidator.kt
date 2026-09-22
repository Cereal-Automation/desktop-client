package com.cereal.client.presentation.view.fields.validator

import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.sdk.statemodifier.ScriptConfig
import java.io.File

class ScriptConfigurationItemDefinitionFileFieldValidator(
    private val definition: ScriptConfigurationItemDefinition,
    private val scriptConfigProvider: () -> ScriptConfig,
) : FileFieldValidator {
    override fun validate(value: File?): String? {
        val scriptConfig = scriptConfigProvider()

        return if (definition.isRequired(scriptConfig) && value == null) {
            "A value is required"
        } else {
            definition.stateModifier?.getError(scriptConfig)
        }
    }
}

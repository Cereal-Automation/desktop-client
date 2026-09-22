package com.cereal.client.presentation.view.fields.validator

import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.sdk.statemodifier.ScriptConfig

class ScriptConfigurationItemDefinitionAnyFieldValidator(
    private val definition: ScriptConfigurationItemDefinition,
    private val scriptConfigProvider: () -> ScriptConfig,
) : AnyFieldValidator {
    override fun validate(value: Any?): String? {
        val scriptConfig = scriptConfigProvider()

        return if (definition.isRequired(scriptConfig) && value == null) {
            "A value is required"
        } else {
            definition.stateModifier?.getError(scriptConfig)
        }
    }
}

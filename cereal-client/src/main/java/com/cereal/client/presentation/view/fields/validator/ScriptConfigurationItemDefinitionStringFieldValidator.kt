package com.cereal.client.presentation.view.fields.validator

import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.sdk.statemodifier.ScriptConfig

class ScriptConfigurationItemDefinitionStringFieldValidator(
    private val definition: ScriptConfigurationItemDefinition,
    private val scriptConfigProvider: () -> ScriptConfig,
) : StringFieldValidator {
    override fun validate(value: String?): String? {
        val scriptConfig = scriptConfigProvider()
        val requiredStringFieldValidator = RequiredStringFieldValidator()
        val requiredError =
            if (definition.isRequired(scriptConfig)) requiredStringFieldValidator.validate(value) else null
        val typeError = if (value?.isNotEmpty() == true) getTypeValidator()?.validate(value) else null

        return requiredError
            ?: typeError ?: definition.stateModifier?.getError(
            scriptConfig,
        )
    }

    /**
     * The format validator for this item's type, or null for types with no checkable format.
     *
     * A secret is in the latter group: any non-empty string is a valid token, and requiredness and
     * emptiness are already handled by [validate].
     */
    private fun getTypeValidator(): StringFieldValidator? =
        when (definition.type) {
            ConfigItemType.BooleanConfigItem -> null
            ConfigItemType.DoubleConfigItem -> DoubleStringFieldValidator()
            is ConfigItemType.EnumConfigItem -> null
            ConfigItemType.FloatConfigItem -> FloatStringFieldValidator()
            is ConfigItemType.GroupedConfigItem -> null
            ConfigItemType.IntConfigItem -> IntStringFieldValidator()
            ConfigItemType.ProxyConfigItem -> null
            ConfigItemType.ProxyGroupConfigItem -> null
            ConfigItemType.SecretConfigItem -> null
            ConfigItemType.StringConfigItem -> null
            is ConfigItemType.ListConfigItem -> null
        }
}

package com.cereal.client.presentation.view.fields.validator

import com.cereal.client.domain.model.script.configuration.ListRows
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.sdk.statemodifier.ScriptConfig

/**
 * List-level validation for a list item: cardinality and the item's own state modifier.
 *
 * Missing values *within* a row are reported by that field's own validator, so the user sees the error
 * next to the field that is wrong rather than as one message for the whole list.
 */
class ScriptConfigurationItemDefinitionListFieldValidator(
    private val definition: ScriptConfigurationItemDefinition,
    private val scriptConfigProvider: () -> ScriptConfig,
) : FieldValidator<ListRows> {
    override fun validate(value: ListRows?): String? {
        val scriptConfig = scriptConfigProvider()
        val stateModifierError = definition.stateModifier?.getError(scriptConfig)
        if (stateModifierError != null) return stateModifierError

        if (!definition.isRequired(scriptConfig)) return null

        // The state only reports rows the user actually entered something in, so any row counts here.
        val hasRow = value?.rows?.isNotEmpty() == true
        return if (hasRow) null else "At least one entry is required"
    }
}

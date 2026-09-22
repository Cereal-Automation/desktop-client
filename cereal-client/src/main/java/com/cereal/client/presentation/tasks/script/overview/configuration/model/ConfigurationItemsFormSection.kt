package com.cereal.client.presentation.tasks.script.overview.configuration.model

import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.toConfigValue
import com.cereal.client.presentation.tasks.script.overview.configuration.RawScriptConfigValues
import com.cereal.client.presentation.tasks.script.overview.configuration.createFormFieldState
import com.cereal.client.presentation.view.fields.state.DropDownFieldState
import com.cereal.client.presentation.view.fields.state.FileFieldState
import com.cereal.client.presentation.view.fields.state.FormFieldState
import com.cereal.client.presentation.view.fields.state.ListFieldState
import com.cereal.client.presentation.view.fields.state.SecretTextFieldState
import com.cereal.client.presentation.view.fields.state.SwitchFieldState
import com.cereal.client.presentation.view.fields.state.TextFieldState
import com.cereal.client.presentation.view.fields.validator.isRequired
import com.cereal.sdk.statemodifier.Visibility

class ConfigurationItemsFormSection(
    private val configurationItems: List<ConfigurationItem>,
    private val onImportFile: (
        configurationItem: ConfigurationItem,
        formFieldState: FormFieldState<*, *>,
        formSection: ConfigurationItemsFormSection,
    ) -> Unit,
) {
    val configurationItemToFieldState: Map<ConfigurationItem, FormFieldState<*, *>> =
        configurationItems.associateWith {
            it.createFormFieldState(
                onValueChange = {
                    applyStateModifiers()
                },
                scriptConfigProvider = {
                    RawScriptConfigValues(getScriptConfigurationValues())
                },
                onImportFile = { configurationItem, formFieldState ->
                    onImportFile(configurationItem, formFieldState, this)
                },
            )
        }

    fun containsRequiredConfigurationItems(): Boolean {
        val configValues = getScriptConfigurationValues()

        return configurationItems.any {
            it.definition.isRequired(RawScriptConfigValues(configValues))
        }
    }

    fun getFormFieldStates(): List<FormFieldState<*, *>> = configurationItemToFieldState.values.toList()

    fun applyStateModifiers() {
        val configValues = getScriptConfigurationValues()
        configurationItemToFieldState.forEach { (configurationItem, formFieldState) ->
            configurationItem.definition.stateModifier?.let { stateModifier ->
                val isVisible = stateModifier.getVisibility(RawScriptConfigValues(configValues)) != Visibility.Hidden
                formFieldState.isVisible.value = isVisible
            }
        }

        configurationItemToFieldState.forEach { (configurationItem, formFieldState) ->
            configurationItem.definition.stateModifier?.let { _ ->
                val isRequired =
                    configurationItem.definition.isRequired(RawScriptConfigValues(configValues))
                formFieldState.isRequired.value = isRequired
            }
        }
    }

    fun getScriptConfigurationValues(): ScriptConfigurationValues {
        val values = mutableMapOf<String, ConfigValue>()

        configurationItemToFieldState.forEach { item ->
            item.value.getValidatedValue()?.let {
                values[item.key.definition.key] = it.toConfigValue()
            }
        }

        return values
    }

    fun applyConfigurationValues(values: ScriptConfigurationValues) {
        configurationItemToFieldState.forEach { (configurationItem, formFieldState) ->
            val key = configurationItem.definition.key
            val value = values[key] ?: return@forEach

            when (formFieldState) {
                // Before the general TextFieldState branch: `raw.toString()` on a secret yields the
                // mask, so routing it through there would overwrite the credential with "***".
                is SecretTextFieldState -> {
                    (value as? ConfigValue.SecretValue)?.raw?.let { formFieldState.onValueChange(it.reveal()) }
                }

                is TextFieldState<*> -> {
                    formFieldState.onValueChange(value.raw.toString())
                }

                is SwitchFieldState -> {
                    (value as? ConfigValue.BooleanValue)?.raw?.let { formFieldState.onValueChange(it) }
                }

                is DropDownFieldState<*> -> {
                    // Find matching value in dropdown options
                    @Suppress("UNCHECKED_CAST")
                    val dropDownState = formFieldState as DropDownFieldState<Any>
                    val rawValue = value.raw
                    val matchingOption =
                        dropDownState.values.value.find { option ->
                            option == rawValue || option.toString() == rawValue.toString()
                        }
                    matchingOption?.let { dropDownState.onValueChange(it) }
                }

                is FileFieldState -> {
                    (value as? ConfigValue.FileValue)?.raw?.let { formFieldState.onValueChange(it) }
                }

                is ListFieldState -> {
                    (value as? ConfigValue.ListValue)?.raw?.let { formFieldState.setRows(it) }
                }
            }
        }
        applyStateModifiers()
    }
}

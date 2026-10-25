package com.cereal.client.presentation.tasks.script.overview.configuration.model

import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.presentation.view.fields.state.FormFieldState

class ConfigurationItemsForm(
    mainConfiguration: List<ConfigurationItem>,
    val childConfigurations: Map<String, List<ConfigurationItem>>,
    onImportFile: (
        configurationItem: ConfigurationItem,
        formFieldState: FormFieldState<*, *>,
        formSection: ConfigurationItemsFormSection,
    ) -> Unit,
) {
    val mainConfigurationFormSection = ConfigurationItemsFormSection(mainConfiguration, onImportFile)
    val childConfigurationFormSections =
        childConfigurations.mapValues { (_, value) -> ConfigurationItemsFormSection(value, onImportFile) }

    private fun getAllFormSections(): List<ConfigurationItemsFormSection> = childConfigurationFormSections.values + mainConfigurationFormSection

    fun getAllFormFieldStates(): List<FormFieldState<*, *>> = getAllFormSections().map { it.getFormFieldStates() }.flatten()

    fun getAllConfigurationItemWithFieldStates(): Map<ConfigurationItem, FormFieldState<*, *>> =
        getAllFormSections()
            .map { it.configurationItemToFieldState }
            .flatMap { it.entries }
            .associate { it.key to it.value }

    fun applyStateModifiers() {
        mainConfigurationFormSection.applyStateModifiers()
        childConfigurationFormSections.forEach { it.value.applyStateModifiers() }
    }

    fun applyConfigurationFromInstance(scriptPackageInstance: ScriptPackageInstance) {
        // Apply main configuration values
        mainConfigurationFormSection.applyConfigurationValues(scriptPackageInstance.mainConfiguration)

        // Apply child configuration values
        scriptPackageInstance.childConfigurations.forEach { (childKey, childValues) ->
            childConfigurationFormSections[childKey]?.applyConfigurationValues(childValues)
        }
    }
}

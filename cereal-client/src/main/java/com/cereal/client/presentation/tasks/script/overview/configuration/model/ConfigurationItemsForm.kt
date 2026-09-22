package com.cereal.client.presentation.tasks.script.overview.configuration.model

import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.presentation.view.fields.state.FormFieldState
import com.cereal.client.presentation.view.fields.state.IntTextFieldState
import com.cereal.client.presentation.view.fields.validator.IntStringFieldValidator
import com.cereal.client.presentation.view.fields.validator.RequiredStringFieldValidator

class ConfigurationItemsForm(
    mainConfiguration: List<ConfigurationItem>,
    val childConfigurations: Map<String, List<ConfigurationItem>>,
    onImportFile: (
        configurationItem: ConfigurationItem,
        formFieldState: FormFieldState<*, *>,
        formSection: ConfigurationItemsFormSection,
    ) -> Unit,
) {
    companion object {
        const val MAX_CONCURRENT_TASKS = 25
    }

    val mainConfigurationFormSection = ConfigurationItemsFormSection(mainConfiguration, onImportFile)
    val childConfigurationFormSections =
        childConfigurations.mapValues { (_, value) -> ConfigurationItemsFormSection(value, onImportFile) }
    val numberOfConcurrentTasks =
        IntTextFieldState(
            validators =
                listOf(
                    RequiredStringFieldValidator(),
                    IntStringFieldValidator(maxValue = MAX_CONCURRENT_TASKS),
                ),
            initialValue = 1,
        )

    private fun getAllFormSections(): List<ConfigurationItemsFormSection> = childConfigurationFormSections.values + mainConfigurationFormSection

    fun getAllFormFieldStates(): List<FormFieldState<*, *>> {
        val states = getAllFormSections().map { it.getFormFieldStates() }.flatten()
        return states + numberOfConcurrentTasks
    }

    fun getAllConfigurationItemWithFieldStates(): Map<ConfigurationItem, FormFieldState<*, *>> =
        getAllFormSections()
            .map { it.configurationItemToFieldState }
            .flatMap { it.entries }
            .associate { it.key to it.value }

    fun getNumberOfConcurrentTasks(): Int = numberOfConcurrentTasks.getValidatedValue()!!

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

        // Apply number of concurrent tasks
        numberOfConcurrentTasks.onValueChange(scriptPackageInstance.numberOfConcurrentTasks.toString())
    }
}

package com.cereal.client.application.interactor.script.validator

import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.asGroup
import com.cereal.client.domain.model.script.configuration.groupedConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.proxyConfigurationDefinition
import com.cereal.client.domain.model.script.entry

data class ScriptConfigurationItemDefinitionConflict(
    val definition: ScriptConfigurationItemDefinition,
    val numberOfRecords: Int,
)

class ScriptConcurrencyValidator(
    private val scriptPackage: ScriptPackage,
    private val mainScriptConfiguration: ScriptConfigurationValues,
    private val childScriptConfiguration: Map<String, ScriptConfigurationValues>,
    private val numberOfConcurrentTasks: Int,
    /** The entry whose configuration is checked; null is the default entry. */
    private val variantId: String? = null,
) {
    fun validateTaskLimits(): ScriptConfigurationItemDefinitionConflict? {
        val scriptConfigurationItemDefinitionConflicts = mutableListOf<ScriptConfigurationItemDefinitionConflict>()

        val mainScriptConfigurationDefinition = scriptPackage.entry(variantId).configuration
        listOfNotNull(
            mainScriptConfigurationDefinition.groupedConfigurationDefinition(),
        ).forEach { definition ->
            checkConflict(definition, mainScriptConfiguration)?.let {
                scriptConfigurationItemDefinitionConflicts.add(it)
            }
        }

        scriptPackage.childScripts.entries.forEach { childScript ->
            val childScriptConfigurationDefinition = childScript.value.configuration

            listOfNotNull(
                childScriptConfigurationDefinition.groupedConfigurationDefinition(),
            ).forEach { definition ->
                checkConflict(definition, childScriptConfiguration[definition.key])?.let {
                    scriptConfigurationItemDefinitionConflicts.add(it)
                }
            }
        }

        // Select the conflict with the least amount of records because numberOfConcurrentTasks will adjust to that.
        return scriptConfigurationItemDefinitionConflicts.minByOrNull { it.numberOfRecords }
    }

    /**
     * Check if there are fewer proxies available than required for the number of concurrent tasks. If so it's
     * a conflict.
     */
    fun validateRecordsReused(): List<ScriptConfigurationItemDefinitionConflict> {
        val scriptConfigurationItemDefinitionConflicts = mutableListOf<ScriptConfigurationItemDefinitionConflict>()

        val mainScriptConfigurationDefinition = scriptPackage.entry(variantId).configuration
        mainScriptConfigurationDefinition.proxyConfigurationDefinition()?.let { definition ->
            checkConflict(definition, mainScriptConfiguration)?.let {
                scriptConfigurationItemDefinitionConflicts.add(it)
            }
        }

        scriptPackage.childScripts.entries.forEach { childScript ->
            val childScriptConfigurationDefinition = childScript.value.configuration

            childScriptConfigurationDefinition.proxyConfigurationDefinition()?.let { definition ->
                checkConflict(definition, childScriptConfiguration[definition.key])?.let {
                    scriptConfigurationItemDefinitionConflicts.add(it)
                }
            }
        }

        return scriptConfigurationItemDefinitionConflicts
    }

    private fun checkConflict(
        definition: ScriptConfigurationItemDefinition,
        values: ScriptConfigurationValues?,
    ): ScriptConfigurationItemDefinitionConflict? =
        values?.get(definition.key)?.asGroup?.let { accountGroup ->
            val count = accountGroup.numberOfItems
            if (count < numberOfConcurrentTasks) {
                ScriptConfigurationItemDefinitionConflict(
                    definition,
                    count,
                )
            } else {
                null
            }
        }
}

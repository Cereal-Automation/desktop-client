package com.cereal.client.application.interactor.script

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ApplicationScriptConfigurationKeys
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.isValidReturnType
import com.cereal.client.domain.repository.CustomDatasetRepository
import com.cereal.client.domain.repository.ProxyRepository
import kotlinx.coroutines.flow.firstOrNull

class GetScriptConfigDefinitionInteractor(
    private val proxyRepository: ProxyRepository,
    private val customDatasetRepository: CustomDatasetRepository,
) : Interactor<GetScriptConfigDefinitionInteractor.Result, GetScriptConfigDefinitionInteractor.Params>() {
    override suspend fun run(params: Params): Result {
        val mainItems =
            params.scriptPackage.mainScript.configuration
                .toConfigurationItems(params.scriptPackageInstance?.mainConfiguration)
                .toMutableList()

        val childConfigurations =
            params.scriptPackage.childScripts
                .map {
                    it.key to
                        it.value.configuration
                            .toConfigurationItems(
                                params.scriptPackageInstance?.childConfigurations?.get(
                                    it.key,
                                ),
                            ).toMutableList()
                }.toMap()

        return Result(mainItems, childConfigurations)
    }

    data class Params(
        val scriptPackage: ScriptPackage,
        val scriptPackageInstance: ScriptPackageInstance?,
    )

    data class Result(
        val mainConfiguration: List<ConfigurationItem>,
        val childConfigurations: Map<String, List<ConfigurationItem>>,
    )

    private suspend fun ScriptConfigurationDefinition.toConfigurationItems(
        scriptConfigurationValues: ScriptConfigurationValues?,
    ): List<ConfigurationItem> =
        this.configurationItems.map { definition ->
            // Use user-provided value if available, otherwise fall back to the default value from the definition.
            // Keep the value only when it matches the item's type; a mismatch degrades to no value.
            val value =
                (scriptConfigurationValues?.get(definition.key) ?: definition.defaultValue)
                    ?.takeIf { definition.isValidReturnType(it) }

            when (val type = definition.type) {
                is ConfigItemType.EnumConfigItem -> {
                    ConfigurationItem(
                        definition = definition,
                        value = value,
                        options =
                            type.enumType.java.enumConstants
                                .map { ConfigValue.EnumValue(it) },
                    )
                }

                ConfigItemType.ProxyConfigItem, ConfigItemType.ProxyGroupConfigItem -> {
                    val proxyGroups = proxyRepository.getProxyGroups().firstOrNull().orEmpty()
                    ConfigurationItem(
                        definition = definition,
                        value = value,
                        options = proxyGroups.map { ConfigValue.ProxyGroupValue(it) },
                    )
                }

                is ConfigItemType.GroupedConfigItem -> {
                    val datasetGroups = customDatasetRepository.getDatasetGroups().firstOrNull().orEmpty()
                    ConfigurationItem(
                        definition = definition,
                        value =
                            scriptConfigurationValues
                                ?.get(ApplicationScriptConfigurationKeys.KEY_CUSTOM_DATASET.key)
                                ?.takeIf { it is ConfigValue.CustomDatasetGroupValue },
                        options = datasetGroups.map { ConfigValue.CustomDatasetGroupValue(it) },
                    )
                }

                else -> {
                    ConfigurationItem(definition = definition, value = value)
                }
            }
        }
}

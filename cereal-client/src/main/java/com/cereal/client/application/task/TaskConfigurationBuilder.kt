package com.cereal.client.application.task

import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealthStatus
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.configuration.ApplicationScriptConfigurationKeys
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.groupedConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.proxyConfigurationDefinition
import com.cereal.client.domain.model.script.getNumberOfConcurrentTasks
import com.cereal.client.domain.repository.CustomDatasetRepository
import com.cereal.client.domain.repository.ProxyRepository
import com.cereal.client.domain.repository.TasksRepository
import kotlinx.coroutines.flow.first
import kotlin.math.max

/**
 * Builds a list with task configurations based on the configuration template of the script instance taking into
 * account already used proxies and custom datasets. The strategy is as follows:
 *
 * - Proxies: lookup already used proxies within the script package instance (so not just the script instance) and build a list of how many times a proxy is used by tasks. The least-used proxies are then used to create the additional tasks.
 * - Custom datasets: lookup already used custom dataset items within the script instance and create additional tasks for the custom data set items that are not used yet.
 */
class TaskConfigurationBuilder(
    private val tasksRepository: TasksRepository,
    private val proxyRepository: ProxyRepository,
    private val customDatasetRepository: CustomDatasetRepository,
) {
    suspend fun get(scriptInstance: ScriptInstance): List<ScriptConfigurationValues> {
        val existingScriptInstanceTasks = tasksRepository.getTasks(scriptInstance)
        val existingScriptInstanceConfigurations = existingScriptInstanceTasks.map { it.configuration }
        val existingScriptPackageInstanceConfigurations = existingScriptInstanceConfigurations

        // TODO: Implement a more abstract/generic approach for group <-> single items setup.
        val proxyConfigurationDefinition = scriptInstance.definition.configuration.proxyConfigurationDefinition()
        val groupedConfigurationDefinition = scriptInstance.definition.configuration.groupedConfigurationDefinition()

        val availableProxies =
            getProxies(
                scriptInstance,
                existingScriptPackageInstanceConfigurations,
                proxyConfigurationDefinition,
            )
        val availableCustomDatasets =
            getAvailableCustomDatasets(
                scriptInstance,
                existingScriptInstanceConfigurations,
                groupedConfigurationDefinition,
            )

        // Determine the amount of new tasks to create in this order:
        // 1. If there's task specific data, use that to determine the amount of tasks based on the one with the least number of data items.
        // 2. Use the users preferred number of tasks provided during configuration of the script.
        // Note: it's on purpose that no proxies are taken into account here because we assume they are reusable.
        val numberOfTasks =
            if (availableCustomDatasets != null) {
                availableCustomDatasets.size
            } else {
                max(
                    0,
                    scriptInstance.getNumberOfConcurrentTasks() - existingScriptInstanceTasks.size,
                )
            }

        // Create task configuration by assigning group items to each task.
        val configurations = mutableListOf<ScriptConfigurationValues>()

        for (index in 0 until numberOfTasks) {
            val templateConfiguration = scriptInstance.configuration.toMutableMap()

            proxyConfigurationDefinition?.let { definition ->
                availableProxies?.let {
                    val proxy = it.getAndIncrementLeastUsedProxy()
                    templateConfiguration.replace(definition.key, ConfigValue.ProxyValue(proxy))
                }
            }
            groupedConfigurationDefinition?.let { definition ->
                availableCustomDatasets?.get(index)?.let {
                    templateConfiguration.replace(definition.key, ConfigValue.CustomDatasetItemValue(it))
                }
            }

            configurations.add(templateConfiguration)
        }

        return configurations
    }

    private suspend fun getProxies(
        scriptInstance: ScriptInstance,
        existingConfigurations: List<ScriptConfigurationValues>,
        proxyConfigurationDefinition: ScriptConfigurationItemDefinition?,
    ): ProxiesRandomizer? =
        proxyConfigurationDefinition?.let { definition ->
            // Single proxies are needed in the configuration, built the list of available proxies by removing the proxies that are already in use by existing tasks.
            (scriptInstance.configuration[definition.key] as? ConfigValue.ProxyGroupValue)?.raw?.let { proxyGroup ->
                buildProxiesRandomizer(proxyGroup, definition, existingConfigurations)
            }
        }

    private suspend fun buildProxiesRandomizer(
        proxyGroup: ProxyGroup,
        definition: ScriptConfigurationItemDefinition,
        existingConfigurations: List<ScriptConfigurationValues>,
    ): ProxiesRandomizer? {
        val proxies =
            proxyRepository
                .getProxiesFromGroup(proxyGroup.id)
                .filter { proxy -> proxy.health.status != ProxyHealthStatus.FAILED }

        if (proxies.isEmpty()) return null

        val proxiesRandomizer = ProxiesRandomizer(proxies)
        existingConfigurations.forEach { configuration ->
            (configuration[definition.key] as? ConfigValue.ProxyValue)?.raw?.let { proxy ->
                proxiesRandomizer.increment(proxy)
            }
        }
        return proxiesRandomizer
    }

    private suspend fun getAvailableCustomDatasets(
        scriptInstance: ScriptInstance,
        existingConfigurations: List<ScriptConfigurationValues>,
        groupedConfigurationDefinition: ScriptConfigurationItemDefinition?,
    ): List<CustomDatasetItem>? =
        groupedConfigurationDefinition?.let { definition ->
            (scriptInstance.configuration[definition.key] as? ConfigValue.CustomDatasetGroupValue)?.raw?.let {
                val availableCustomDatasets = customDatasetRepository.getDatasets(it).first().toMutableList()
                availableCustomDatasets.removeIf { customDataset ->
                    existingConfigurations.any { configuration ->
                        (configuration[ApplicationScriptConfigurationKeys.KEY_CUSTOM_DATASET.key] as? ConfigValue.CustomDatasetItemValue)?.raw?.id ==
                            customDataset.id
                    }
                }
                availableCustomDatasets
            }
        }
}

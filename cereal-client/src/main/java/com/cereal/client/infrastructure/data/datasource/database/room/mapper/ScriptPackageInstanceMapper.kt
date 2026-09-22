package com.cereal.client.infrastructure.data.datasource.database.room.mapper

import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ApplicationScriptConfigurationKeys
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptConfigurationEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptConfigurationItemEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptPackageEntity
import kotlin.time.ExperimentalTime

/**
 * Room mapper for converting ScriptPackage entities to ScriptPackageInstance domain models
 */
class ScriptPackageInstanceMapper(
    private val keyValueMapper: KeyValueRoomMapper,
) {
    @OptIn(ExperimentalTime::class)
    suspend fun mapToScriptPackageInstance(
        scriptPackageEntity: ScriptPackageEntity,
        mainScriptEntity: ScriptEntity?,
        mainScriptConfigurationItems: List<ScriptConfigurationItemEntity>,
        childScriptConfigurations: List<ScriptConfigurationEntity>,
        childScriptConfigurationItems: Map<String, List<ScriptConfigurationItemEntity>>,
        scriptPackageDefinition: ScriptPackage,
        user: User,
        notificationOverrides: ScriptNotificationOverrides? = null,
    ): ScriptPackageInstance? {
        if (mainScriptEntity == null) {
            return null
        }

        // Map child script configurations
        val childScriptConfigurationValues =
            childScriptConfigurations
                .mapNotNull { scriptConfigurationEntity ->
                    // Exclude any child scripts instances for which there are no child script definitions found.
                    scriptPackageDefinition.childScripts[scriptConfigurationEntity.scriptId]?.let {
                        scriptConfigurationEntity
                    }
                }.associate { configEntity ->
                    val configItems = childScriptConfigurationItems[configEntity.scriptId] ?: emptyList()
                    configEntity.scriptId!! to
                        keyValueMapper.mapConfigurationItemsFromEntities(
                            configItems,
                            user,
                            scriptPackageDefinition.childScripts[configEntity.scriptId]!!.configuration,
                        )
                }

        // Map main script configuration
        val mainScriptConfigurationValues =
            keyValueMapper.mapConfigurationItemsFromEntities(
                mainScriptConfigurationItems,
                user,
                scriptPackageDefinition.mainScript.configuration,
            )

        // BC note: for script started after this version is released numberOfConcurrentTasks should always be
        // BC note: set, for older created script instances the value is optionally in the configuration of the main
        // BC note: script config or else fall back to 1.
        @Suppress("DEPRECATION")
        val numberOfConcurrentTasks =
            scriptPackageEntity.numberOfConcurrentTasks
                ?: (mainScriptConfigurationValues[ApplicationScriptConfigurationKeys.KEY_NUMBER_OF_TASKS.key] as? ConfigValue.IntValue)?.raw
                ?: 1

        return ScriptPackageInstance(
            id = scriptPackageEntity.id.toString(),
            mainConfiguration = mainScriptConfigurationValues,
            childConfigurations = childScriptConfigurationValues,
            definition = scriptPackageDefinition,
            createdAt = scriptPackageEntity.createdAt,
            numberOfConcurrentTasks = numberOfConcurrentTasks,
            notificationOverrides = notificationOverrides,
        )
    }
}

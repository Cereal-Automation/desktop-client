package com.cereal.client.infrastructure.data.datasource.database.room.mapper

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.client.domain.model.script.configuration.toConfigValue
import com.cereal.client.infrastructure.data.datasource.database.room.entity.DatasetEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.DatasetGroupEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.DatasetGroupItemDefinitionEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.DatasetItemEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ValueType
import com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString
import java.util.UUID
import kotlin.reflect.full.isSubclassOf
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Mapper for converting between domain models and Room entities for dataset operations
 */
class DatasetMapper {
    private val valueTypeMapping =
        mapOf(
            ValueType.INT to ConfigItemType.IntConfigItem,
            ValueType.STRING to ConfigItemType.StringConfigItem,
            ValueType.SECRET to ConfigItemType.SecretConfigItem,
            ValueType.BOOLEAN to ConfigItemType.BooleanConfigItem,
            ValueType.FLOAT to ConfigItemType.FloatConfigItem,
            ValueType.DOUBLE to ConfigItemType.DoubleConfigItem,
        )

    @OptIn(ExperimentalTime::class)
    fun createDatasetGroupEntity(customDatasetGroup: CustomDatasetGroup): DatasetGroupEntity {
        val now =
            Clock.System
                .now()
        return DatasetGroupEntity(
            id = UUID.fromString(customDatasetGroup.id),
            name = customDatasetGroup.name,
            createdAt = now,
            updatedAt = now,
        )
    }

    @OptIn(ExperimentalTime::class)
    fun updateDatasetGroupEntity(
        existing: DatasetGroupEntity,
        customDatasetGroup: CustomDatasetGroup,
    ): DatasetGroupEntity =
        existing.copy(
            name = customDatasetGroup.name,
            updatedAt =
                Clock.System
                    .now(),
        )

    @OptIn(ExperimentalTime::class)
    fun createDatasetGroupItemDefinitionEntities(
        customDatasetGroup: CustomDatasetGroup,
    ): List<DatasetGroupItemDefinitionEntity> {
        val now =
            Clock.System
                .now()
        val groupId = UUID.fromString(customDatasetGroup.id)

        return customDatasetGroup.itemDefinitions.map { definition ->
            val valueType =
                valueTypeMapping.entries
                    .find { definition.type.scriptValueType.isSubclassOf(it.value.scriptValueType) }
                    ?.key
                    ?: throw RuntimeException("Can't perform mapping because of an unsupported type: ${definition.type}")

            DatasetGroupItemDefinitionEntity(
                id = UUID.randomUUID(),
                groupId = groupId,
                key = definition.key,
                name = definition.name,
                description = definition.description,
                type = valueType,
                position = definition.position,
                isNullable = definition.isNullable,
                isScriptIdentifier = definition.isScriptIdentifier,
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    fun createDatasetEntity(
        customDatasetItem: CustomDatasetItem,
        groupId: String,
    ): DatasetEntity {
        val now =
            Clock.System
                .now()
        return DatasetEntity(
            id = customDatasetItem.id,
            groupId = UUID.fromString(groupId),
            createdAt = now,
            updatedAt = now,
        )
    }

    @OptIn(ExperimentalTime::class)
    fun createDatasetItemEntities(
        customDatasetItem: CustomDatasetItem,
    ): List<DatasetItemEntity> {
        val now =
            Clock.System
                .now()

        // Only store non-null values. When the CustomDatasetItem is retrieved from the database
        // all the fields not known in the database are created with a null value.
        return customDatasetItem.fields.filterValues { it != null }.map { (key, value) ->
            DatasetItemEntity(
                id = UUID.randomUUID(),
                datasetId = customDatasetItem.id,
                key = key,
                value = EncryptedString.of(value.storedText()),
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    fun createDatasetsFromGroup(customDatasetGroup: CustomDatasetGroup): List<DatasetEntity> {
        val now =
            Clock.System
                .now()
        val groupId = UUID.fromString(customDatasetGroup.id)

        return customDatasetGroup.items
            .map { customDatasetItem ->
                DatasetEntity(
                    id = customDatasetItem.id,
                    groupId = groupId,
                    createdAt = now,
                    updatedAt = now,
                )
            }.toList()
    }

    fun createDatasetItemEntitiesFromGroup(customDatasetGroup: CustomDatasetGroup): List<DatasetItemEntity> =
        customDatasetGroup.items
            .flatMap { customDatasetItem ->
                createDatasetItemEntities(customDatasetItem)
            }.toList()

    fun toDomain(
        groupEntity: DatasetGroupEntity,
        itemDefinitions: List<DatasetGroupItemDefinitionEntity>,
        datasets: List<DatasetEntity>,
        datasetItems: Map<UUID, List<DatasetItemEntity>>,
    ): CustomDatasetGroup {
        val scriptItemDefinitions =
            itemDefinitions
                .sortedBy { it.position }
                .map { entity ->
                    val type =
                        valueTypeMapping[entity.type]
                            ?: throw RuntimeException("Can't perform mapping because of an unsupported type: ${entity.type}")

                    ScriptConfigurationItemDefinition(
                        key = entity.key,
                        name = entity.name,
                        description = entity.description,
                        position = entity.position,
                        type = type,
                        isNullable = entity.isNullable,
                        stateModifier = null,
                        isScriptIdentifier = entity.isScriptIdentifier,
                    )
                }

        val customDatasetItems =
            datasets.map { datasetEntity ->
                val items = datasetItems[datasetEntity.id] ?: emptyList()
                toDomain(datasetEntity, items, itemDefinitions)
            }

        return CustomDatasetGroup(
            id = groupEntity.id.toString(),
            name = groupEntity.name,
            numberOfItems = datasets.size,
            itemDefinitions = scriptItemDefinitions,
            items = customDatasetItems.asSequence(),
            createdAt = groupEntity.createdAt,
        )
    }

    fun toDomain(
        datasetEntity: DatasetEntity,
        datasetItems: List<DatasetItemEntity>,
        itemDefinitions: List<DatasetGroupItemDefinitionEntity>,
    ): CustomDatasetItem {
        val fieldsMap =
            datasetItems.associate { item ->
                item.key to parseValue(item.value.value ?: "", item.key, itemDefinitions)?.toConfigValue()
            }

        // Add missing fields with null values
        val missingFields =
            itemDefinitions
                .filter { expectedDefinition -> !fieldsMap.containsKey(expectedDefinition.key) }
                .associate { it.key to null }

        return CustomDatasetItem(
            id = datasetEntity.id,
            fields = missingFields + fieldsMap,
        )
    }

    /**
     * The text stored for a dataset field value.
     *
     * A [Secret] must be unwrapped here rather than rendered: its `toString` is the mask, so the
     * default rendering would persist `***` and destroy the credential. The column is an
     * [EncryptedString] either way, so per-task credentials are encrypted at rest exactly as every
     * other dataset value already is — the type label is the only thing that differs.
     */
    private fun ConfigValue?.storedText(): String =
        when (val raw = this?.raw) {
            is Secret -> raw.reveal()
            else -> raw.toString()
        }

    private fun parseValue(
        value: String,
        key: String,
        itemDefinitions: List<DatasetGroupItemDefinitionEntity>,
    ): Any? {
        if (value.isEmpty()) return null

        val definition =
            itemDefinitions.find { it.key == key }
                ?: return value // Default to string if definition not found

        return when (definition.type) {
            ValueType.STRING -> value
            ValueType.SECRET -> Secret(value)
            ValueType.INT -> value.toIntOrNull()
            ValueType.BOOLEAN -> value.toBooleanStrictOrNull()
            ValueType.FLOAT -> value.toFloatOrNull()
            ValueType.DOUBLE -> value.toDoubleOrNull()
            ValueType.LONG -> value.toLongOrNull()
            ValueType.SHORT -> value.toShortOrNull()
            else -> value // Default to string for unsupported types
        }
    }
}

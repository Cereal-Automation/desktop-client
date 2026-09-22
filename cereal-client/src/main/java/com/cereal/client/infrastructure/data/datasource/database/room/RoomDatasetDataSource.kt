package com.cereal.client.infrastructure.data.datasource.database.room

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.DatasetDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.DatasetMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Room implementation of DatasetDataSource
 */
@OptIn(ExperimentalTime::class)
class RoomDatasetDataSource(
    private val roomDatabases: RoomDatabases,
    private val datasetMapper: DatasetMapper,
) : DatasetDataSource {
    override suspend fun getDatasetGroups(user: User): Flow<List<CustomDatasetGroup>> {
        val database = roomDatabases.getUserDatabase(user)
        val dao = database.datasetDao()

        return dao.getAllDatasetGroupsFlow().map { groupEntities ->
            val allDatasets = dao.getAllDatasets().groupBy { it.groupId }

            val allDatasetIds = allDatasets.values.flatten().map { it.id }
            val allDatasetItems =
                if (allDatasetIds.isNotEmpty()) {
                    dao
                        .getDatasetItemsByDatasetIds(allDatasetIds)
                        .groupBy { it.datasetId }
                } else {
                    emptyMap()
                }

            val allItemDefinitions = dao.getAllDatasetGroupItemDefinitions().groupBy { it.groupId }

            groupEntities.map { groupEntity ->
                val itemDefinitions = allItemDefinitions[groupEntity.id] ?: emptyList()
                val datasets = allDatasets[groupEntity.id] ?: emptyList()
                val datasetItems =
                    datasets.associate { dataset ->
                        dataset.id to (allDatasetItems[dataset.id] ?: emptyList())
                    }

                datasetMapper.toDomain(groupEntity, itemDefinitions, datasets, datasetItems)
            }
        }
    }

    override suspend fun createDatasetGroup(
        user: User,
        customDatasetGroup: CustomDatasetGroup,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            val dao = userDatabase.datasetDao()

            val groupEntity = datasetMapper.createDatasetGroupEntity(customDatasetGroup)
            val itemDefinitionEntities = datasetMapper.createDatasetGroupItemDefinitionEntities(customDatasetGroup)

            dao.insertDatasetGroup(groupEntity)
            // Use batch insert for better performance
            dao.insertDatasetGroupItemDefinitions(itemDefinitionEntities)

            // Create datasets from the group
            val datasetEntities = datasetMapper.createDatasetsFromGroup(customDatasetGroup)
            val datasetItemEntities = datasetMapper.createDatasetItemEntitiesFromGroup(customDatasetGroup)

            // Use batch insert for better performance
            if (datasetEntities.isNotEmpty()) {
                dao.insertDatasets(datasetEntities)
            }
            // Use batch insert for better performance
            if (datasetItemEntities.isNotEmpty()) {
                dao.insertDatasetItems(datasetItemEntities)
            }
        }
    }

    override suspend fun updateDatasetGroup(
        user: User,
        customDatasetGroup: CustomDatasetGroup,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            val dao = userDatabase.datasetDao()

            val groupId = UUID.fromString(customDatasetGroup.id)
            val existingGroup =
                dao.getDatasetGroupById(groupId)
                    ?: throw IllegalArgumentException("Dataset group not found: ${customDatasetGroup.id}")

            val updatedGroup = datasetMapper.updateDatasetGroupEntity(existingGroup, customDatasetGroup)
            dao.updateDatasetGroup(updatedGroup)

            // Update item definitions - for simplicity, delete and recreate
            val existingDefinitions = dao.getDatasetGroupItemDefinitionsByGroupId(groupId)
            existingDefinitions.forEach { dao.deleteDatasetGroupItemDefinition(it) }

            val newDefinitions = datasetMapper.createDatasetGroupItemDefinitionEntities(customDatasetGroup)
            newDefinitions.forEach { dao.insertDatasetGroupItemDefinition(it) }
        }
    }

    override suspend fun deleteDatasetGroup(
        user: User,
        customDatasetGroupId: String,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            val dao = userDatabase.datasetDao()

            val groupId = UUID.fromString(customDatasetGroupId)
            dao.getDatasetGroupById(groupId)?.let {
                dao.deleteDatasetGroup(it)
                // Foreign key constraints will handle cascading deletes
            }
        }
    }

    override suspend fun getDatasetGroup(
        user: User,
        id: String,
    ): CustomDatasetGroup? {
        val database = roomDatabases.getUserDatabase(user)
        val dao = database.datasetDao()

        val groupId = UUID.fromString(id)
        val groupEntity = dao.getDatasetGroupById(groupId) ?: return null
        val itemDefinitions = dao.getDatasetGroupItemDefinitionsByGroupId(groupId)
        val datasets = dao.getDatasetsByGroupId(groupId)

        // Use batch query to get all dataset items at once
        val allDatasetItems =
            if (datasets.isNotEmpty()) {
                dao
                    .getDatasetItemsByDatasetIds(datasets.map { it.id })
                    .groupBy { it.datasetId }
            } else {
                emptyMap()
            }

        val datasetItems =
            datasets.associate { dataset ->
                dataset.id to (allDatasetItems[dataset.id] ?: emptyList())
            }

        return datasetMapper.toDomain(groupEntity, itemDefinitions, datasets, datasetItems)
    }

    override suspend fun getDatasetsFromGroup(
        user: User,
        datasetGroupId: String,
    ): List<CustomDatasetItem> {
        val database = roomDatabases.getUserDatabase(user)
        val dao = database.datasetDao()

        val groupId = UUID.fromString(datasetGroupId)
        val datasets = dao.getDatasetsByGroupId(groupId)
        val itemDefinitions = dao.getDatasetGroupItemDefinitionsByGroupId(groupId)

        // Use batch query to get all dataset items at once
        val allDatasetItems =
            if (datasets.isNotEmpty()) {
                dao
                    .getDatasetItemsByDatasetIds(datasets.map { it.id })
                    .groupBy { it.datasetId }
            } else {
                emptyMap()
            }

        return datasets.map { datasetEntity ->
            val datasetItems = allDatasetItems[datasetEntity.id] ?: emptyList()
            datasetMapper.toDomain(datasetEntity, datasetItems, itemDefinitions)
        }
    }

    override suspend fun getDatasetsInGroupCount(
        user: User,
        datasetGroupId: String,
    ): Long {
        val database = roomDatabases.getUserDatabase(user)
        val dao = database.datasetDao()

        val groupId = UUID.fromString(datasetGroupId)
        return dao.getDatasetsCountByGroupId(groupId)
    }

    override fun getDatasetsFlow(
        user: User,
        customDatasetGroupId: String,
    ): Flow<List<CustomDatasetItem>> {
        val database = roomDatabases.getUserDatabase(user)
        val dao = database.datasetDao()

        val groupId = UUID.fromString(customDatasetGroupId)
        return dao.getDatasetsByGroupIdFlow(groupId).map { datasets ->
            val itemDefinitions = dao.getDatasetGroupItemDefinitionsByGroupId(groupId)

            // Use batch query to get all dataset items at once
            val allDatasetItems =
                if (datasets.isNotEmpty()) {
                    dao
                        .getDatasetItemsByDatasetIds(datasets.map { it.id })
                        .groupBy { it.datasetId }
                } else {
                    emptyMap()
                }

            datasets.map { datasetEntity ->
                val datasetItems = allDatasetItems[datasetEntity.id] ?: emptyList()
                datasetMapper.toDomain(datasetEntity, datasetItems, itemDefinitions)
            }
        }
    }

    override suspend fun getDataset(
        user: User,
        id: String,
    ): CustomDatasetItem? {
        val database = roomDatabases.getUserDatabase(user)
        val dao = database.datasetDao()

        val datasetId = UUID.fromString(id)
        val datasetEntity = dao.getDatasetById(datasetId) ?: return null
        val datasetItems = dao.getDatasetItemsByDatasetId(datasetId)
        val itemDefinitions = dao.getDatasetGroupItemDefinitionsByGroupId(datasetEntity.groupId)

        return datasetMapper.toDomain(datasetEntity, datasetItems, itemDefinitions)
    }

    override suspend fun createOrUpdateDataset(
        user: User,
        customDatasetItem: CustomDatasetItem,
        groupId: String,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            val dao = userDatabase.datasetDao()

            // Check if dataset already exists
            val existingDataset = dao.getDatasetById(customDatasetItem.id)

            val datasetEntity =
                existingDataset // Update existing dataset
                    ?.copy(updatedAt = Clock.System.now())
                    ?: // Create new dataset - ensure it's associated with the correct group
                    datasetMapper.createDatasetEntity(customDatasetItem, groupId)

            dao.insertDataset(datasetEntity)

            // Delete existing dataset items and insert new ones
            val existingItems = dao.getDatasetItemsByDatasetId(customDatasetItem.id)
            existingItems.forEach { dao.deleteDatasetItem(it) }

            val newItems = datasetMapper.createDatasetItemEntities(customDatasetItem)
            newItems.forEach { dao.insertDatasetItem(it) }
        }
    }

    override suspend fun deleteDataset(
        user: User,
        customDatasetItemId: UUID,
    ) {
        val userDatabase = roomDatabases.getUserDatabase(user)
        userDatabase.immediateWriteTransaction {
            val database = roomDatabases.getUserDatabase(user)
            val dao = database.datasetDao()

            dao.deleteDatasetById(customDatasetItemId)
            // Foreign key constraints will handle cascading deletes for dataset items
        }
    }
}

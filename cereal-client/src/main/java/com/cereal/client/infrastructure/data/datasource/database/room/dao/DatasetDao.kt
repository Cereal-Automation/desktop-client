package com.cereal.client.infrastructure.data.datasource.database.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cereal.client.infrastructure.data.datasource.database.room.entity.DatasetEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.DatasetGroupEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.DatasetGroupItemDefinitionEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.DatasetItemEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Room DAO for dataset operations.
 *
 * Large DAO interface with many query methods; grouping them here keeps related operations together.
 */
@Suppress("TooManyFunctions")
@Dao
interface DatasetDao {
    // Dataset Group operations
    @Query("SELECT * FROM dataset_group WHERE id = :id")
    suspend fun getDatasetGroupById(id: UUID): DatasetGroupEntity?

    @Query("SELECT * FROM dataset_group")
    fun getAllDatasetGroupsFlow(): Flow<List<DatasetGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDatasetGroup(entity: DatasetGroupEntity)

    @Update
    suspend fun updateDatasetGroup(entity: DatasetGroupEntity)

    @Delete
    suspend fun deleteDatasetGroup(entity: DatasetGroupEntity)

    // Dataset operations
    @Query("SELECT * FROM dataset WHERE id = :id")
    suspend fun getDatasetById(id: UUID): DatasetEntity?

    @Query("SELECT * FROM dataset WHERE group_id = :groupId")
    suspend fun getDatasetsByGroupId(groupId: UUID): List<DatasetEntity>

    @Query("SELECT * FROM dataset WHERE group_id = :groupId")
    fun getDatasetsByGroupIdFlow(groupId: UUID): Flow<List<DatasetEntity>>

    @Query("SELECT COUNT(*) FROM dataset WHERE group_id = :groupId")
    suspend fun getDatasetsCountByGroupId(groupId: UUID): Long

    @Query("SELECT * FROM dataset")
    suspend fun getAllDatasets(): List<DatasetEntity>

    @Query("SELECT * FROM dataset")
    fun getAllDatasetsFlow(): Flow<List<DatasetEntity>>

    @Query("DELETE FROM dataset WHERE id = :id")
    suspend fun deleteDatasetById(id: UUID)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDataset(entity: DatasetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDatasets(entities: List<DatasetEntity>)

    @Update
    suspend fun updateDataset(entity: DatasetEntity)

    @Delete
    suspend fun deleteDataset(entity: DatasetEntity)

    // Dataset Item operations
    @Query("SELECT * FROM dataset_item WHERE dataset_id = :datasetId")
    suspend fun getDatasetItemsByDatasetId(datasetId: UUID): List<DatasetItemEntity>

    // Batch operation to get dataset items for multiple datasets
    @Query("SELECT * FROM dataset_item WHERE dataset_id IN (:datasetIds)")
    suspend fun getDatasetItemsByDatasetIds(datasetIds: List<UUID>): List<DatasetItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDatasetItem(entity: DatasetItemEntity)

    // Batch insert for dataset items - performance optimization
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDatasetItems(entities: List<DatasetItemEntity>)

    @Update
    suspend fun updateDatasetItem(entity: DatasetItemEntity)

    // Batch update for dataset items
    @Update
    suspend fun updateDatasetItems(entities: List<DatasetItemEntity>)

    @Delete
    suspend fun deleteDatasetItem(entity: DatasetItemEntity)

    // Batch delete for dataset items
    @Delete
    suspend fun deleteDatasetItems(entities: List<DatasetItemEntity>)

    // Dataset Group Item Definition operations
    @Query("SELECT * FROM dataset_group_item_definition WHERE group_id = :groupId")
    suspend fun getDatasetGroupItemDefinitionsByGroupId(groupId: UUID): List<DatasetGroupItemDefinitionEntity>

    @Query("SELECT * FROM dataset_group_item_definition")
    suspend fun getAllDatasetGroupItemDefinitions(): List<DatasetGroupItemDefinitionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDatasetGroupItemDefinition(entity: DatasetGroupItemDefinitionEntity)

    // Batch insert for dataset group item definitions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDatasetGroupItemDefinitions(entities: List<DatasetGroupItemDefinitionEntity>)

    @Update
    suspend fun updateDatasetGroupItemDefinition(entity: DatasetGroupItemDefinitionEntity)

    @Delete
    suspend fun deleteDatasetGroupItemDefinition(entity: DatasetGroupItemDefinitionEntity)

    // Optimized query to get complete dataset group with all related data in one go
    @Query(
        """
        SELECT
            dg.id as group_id,
            dg.name as group_name,
            dg.created_at as group_created_at,
            dg.updated_at as group_updated_at,
            d.id as dataset_id,
            d.group_id as dataset_group_id,
            d.created_at as dataset_created_at,
            d.updated_at as dataset_updated_at
        FROM dataset_group dg
        LEFT JOIN dataset d ON dg.id = d.group_id
        WHERE dg.id = :groupId
    """,
    )
    suspend fun getDatasetGroupWithDatasets(groupId: UUID): List<DatasetGroupWithDatasetsResult>

    // Optimized query to get all dataset items for a group in one query
    @Query(
        """
        SELECT di.*
        FROM dataset_item di
        INNER JOIN dataset d ON di.dataset_id = d.id
        WHERE d.group_id = :groupId
    """,
    )
    suspend fun getAllDatasetItemsForGroup(groupId: UUID): List<DatasetItemEntity>
}

// Result class for the optimized query
// Property names mirror the SQL column aliases in getDatasetGroupWithDatasets; renaming would break Room column mapping.
@Suppress("ConstructorParameterNaming")
data class DatasetGroupWithDatasetsResult
    @OptIn(ExperimentalTime::class)
    constructor(
        val group_id: UUID,
        val group_name: String,
        val group_created_at: Instant,
        val group_updated_at: Instant,
        val dataset_id: UUID?,
        val dataset_group_id: UUID?,
        val dataset_created_at: Instant?,
        val dataset_updated_at: Instant?,
    )

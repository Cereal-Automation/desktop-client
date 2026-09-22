package com.cereal.client.infrastructure.data.datasource.database.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptConfigurationEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptConfigurationItemEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptNotificationOverrideEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptPackageEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptPackageGroupEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptPackageGroupWithCountEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptParameterEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.TaskConfigurationEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.TaskEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.TaskStatusEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Room DAO for script instance operations.
 *
 * Large DAO interface with many query methods; grouping them here keeps related operations together.
 */
@Suppress("TooManyFunctions")
@Dao
interface ScriptInstanceDao {
    // Script Package Group operations
    @Query("SELECT * FROM script_package_group")
    fun getAllScriptPackageGroupsFlow(): Flow<List<ScriptPackageGroupEntity>>

    @Query(
        """
        SELECT spg.*, COUNT(sp.id) as scriptPackageCount
        FROM script_package_group spg
        LEFT JOIN script_package sp ON spg.id = sp.group_id
        GROUP BY spg.id
    """,
    )
    fun getAllScriptPackageGroupsWithCountsFlow(): Flow<List<ScriptPackageGroupWithCountEntity>>

    @Query("SELECT * FROM script_package_group WHERE id = :id")
    suspend fun getScriptPackageGroupById(id: UUID): ScriptPackageGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScriptPackageGroup(entity: ScriptPackageGroupEntity)

    @Update
    suspend fun updateScriptPackageGroup(entity: ScriptPackageGroupEntity)

    @Query("DELETE FROM script_package_group WHERE id = :id")
    suspend fun deleteScriptPackageGroupById(id: UUID)

    // Script Package operations
    @Query("SELECT * FROM script_package WHERE id = :id")
    suspend fun getScriptPackageById(id: UUID): ScriptPackageEntity?

    @Query("SELECT * FROM script_package WHERE group_id = :groupId")
    suspend fun getScriptPackagesByGroupId(groupId: UUID): List<ScriptPackageEntity>

    @Query("SELECT * FROM script_package WHERE group_id = :groupId")
    fun getScriptPackagesByGroupIdFlow(groupId: UUID): Flow<List<ScriptPackageEntity>>

    @Query("SELECT * FROM script_package")
    suspend fun getAllScriptPackages(): List<ScriptPackageEntity>

    @Query("SELECT * FROM script_package")
    fun getAllScriptPackagesFlow(): Flow<List<ScriptPackageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScriptPackage(entity: ScriptPackageEntity)

    @Update
    suspend fun updateScriptPackage(entity: ScriptPackageEntity)

    @Query("UPDATE script_package SET group_id = :newGroupId WHERE id = :packageId")
    suspend fun updateScriptPackageGroupId(
        packageId: UUID,
        newGroupId: UUID,
    )

    @Delete
    suspend fun deleteScriptPackage(entity: ScriptPackageEntity)

    @Query("DELETE FROM script_package WHERE id = :id")
    suspend fun deleteScriptPackageById(id: UUID)

    // Script operations
    @Query("SELECT * FROM script WHERE id = :id")
    suspend fun getScriptById(id: UUID): ScriptEntity?

    @Query("SELECT * FROM script WHERE package_id = :packageId")
    suspend fun getScriptsByPackageId(packageId: UUID): List<ScriptEntity>

    @Query("SELECT * FROM script WHERE package_id = :packageId AND parent_script_id IS NULL")
    suspend fun getMainScriptByPackageId(packageId: UUID): ScriptEntity?

    @Query("SELECT * FROM script WHERE parent_script_id = :parentScriptId")
    suspend fun getChildScriptsByParentId(parentScriptId: UUID): List<ScriptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(entity: ScriptEntity)

    @Update
    suspend fun updateScript(entity: ScriptEntity)

    @Delete
    suspend fun deleteScript(entity: ScriptEntity)

    // Script Parameter operations
    @Query("SELECT * FROM script_parameter WHERE script_id = :scriptId")
    suspend fun getScriptParametersByScriptId(scriptId: UUID): List<ScriptParameterEntity>

    // Batch operation to get script parameters for multiple scripts
    @Query("SELECT * FROM script_parameter WHERE script_id IN (:scriptIds)")
    suspend fun getScriptParametersByScriptIds(scriptIds: List<UUID>): List<ScriptParameterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScriptParameter(entity: ScriptParameterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScriptParameters(entities: List<ScriptParameterEntity>)

    @Query("DELETE FROM script_parameter WHERE script_id = :scriptId")
    suspend fun deleteScriptParametersByScriptId(scriptId: UUID)

    // Script Configuration operations
    @Query("SELECT * FROM script_configuration WHERE package_id = :packageId AND is_main_configuration = 1")
    suspend fun getMainScriptConfigurationByPackageId(packageId: UUID): ScriptConfigurationEntity?

    @Query("SELECT * FROM script_configuration WHERE package_id = :packageId AND is_main_configuration = 0")
    suspend fun getChildScriptConfigurationsByPackageId(packageId: UUID): List<ScriptConfigurationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScriptConfiguration(entity: ScriptConfigurationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScriptConfigurations(entities: List<ScriptConfigurationEntity>)

    // Script Configuration Item operations
    @Query("SELECT * FROM script_configuration_item WHERE configuration_id = :configurationId")
    suspend fun getScriptConfigurationItemsByConfigurationId(configurationId: UUID): List<ScriptConfigurationItemEntity>

    // Batch operation to get script configuration items for multiple configurations
    @Query("SELECT * FROM script_configuration_item WHERE configuration_id IN (:configurationIds)")
    suspend fun getScriptConfigurationItemsByConfigurationIds(configurationIds: List<UUID>): List<ScriptConfigurationItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScriptConfigurationItem(entity: ScriptConfigurationItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScriptConfigurationItems(entities: List<ScriptConfigurationItemEntity>)

    // Task operations
    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun getTaskById(id: UUID): TaskEntity?

    @Query("SELECT * FROM task WHERE script_id = :scriptId")
    suspend fun getTasksByScriptId(scriptId: UUID): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(entity: TaskEntity)

    @Update
    suspend fun updateTask(entity: TaskEntity)

    @Delete
    suspend fun deleteTask(entity: TaskEntity)

    @Query("DELETE FROM task WHERE id = :id")
    suspend fun deleteTaskById(id: UUID)

    // Task Configuration operations
    @Query("SELECT * FROM task_configuration WHERE task_id = :taskId")
    suspend fun getTaskConfigurationsByTaskId(taskId: UUID): List<TaskConfigurationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskConfiguration(entity: TaskConfigurationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskConfigurations(entities: List<TaskConfigurationEntity>)

    // Task Status operations
    @Query("SELECT * FROM task_status WHERE task_id = :taskId ORDER BY timestamp ASC")
    suspend fun getTaskStatusesByTaskId(taskId: UUID): List<TaskStatusEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskStatus(entity: TaskStatusEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskStatuses(entities: List<TaskStatusEntity>)

    // Script Notification Override operations
    @Query("SELECT * FROM script_notification_override WHERE package_id = :packageId")
    suspend fun getScriptNotificationOverrideByPackageId(packageId: UUID): ScriptNotificationOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScriptNotificationOverride(entity: ScriptNotificationOverrideEntity)

    @Query("DELETE FROM script_notification_override WHERE package_id = :packageId")
    suspend fun deleteScriptNotificationOverrideByPackageId(packageId: UUID)
}

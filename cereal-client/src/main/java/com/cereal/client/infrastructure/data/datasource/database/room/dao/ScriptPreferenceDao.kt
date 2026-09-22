package com.cereal.client.infrastructure.data.datasource.database.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptPreferenceEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Room DAO for script preference operations
 */
@Dao
interface ScriptPreferenceDao {
    @Query("SELECT * FROM script_preference WHERE id = :id")
    suspend fun getById(id: UUID): ScriptPreferenceEntity?

    @Query("SELECT * FROM script_preference WHERE instance_id = :instanceId")
    suspend fun getByInstanceId(instanceId: String): List<ScriptPreferenceEntity>

    @Query("SELECT * FROM script_preference WHERE instance_id = :instanceId AND key = :key")
    suspend fun getByInstanceIdAndKey(
        instanceId: String,
        key: String,
    ): ScriptPreferenceEntity?

    @Query("SELECT * FROM script_preference WHERE instance_id = :instanceId AND key = :key")
    fun getByInstanceIdAndKeyFlow(
        instanceId: String,
        key: String,
    ): Flow<ScriptPreferenceEntity?>

    @Query("SELECT * FROM script_preference")
    fun getAllFlow(): Flow<List<ScriptPreferenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScriptPreferenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ScriptPreferenceEntity>)

    @Update
    suspend fun update(entity: ScriptPreferenceEntity)

    @Delete
    suspend fun delete(entity: ScriptPreferenceEntity)

    @Query("DELETE FROM script_preference WHERE instance_id = :instanceId")
    suspend fun deleteByInstanceId(instanceId: String)

    @Query("DELETE FROM script_preference WHERE instance_id = :instanceId AND key = :key")
    suspend fun deleteByInstanceIdAndKey(
        instanceId: String,
        key: String,
    )

    @Query("DELETE FROM script_preference")
    suspend fun deleteAll()
}

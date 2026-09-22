package com.cereal.client.infrastructure.data.datasource.database.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cereal.client.infrastructure.data.datasource.database.room.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for application settings operations
 */
@Dao
interface ApplicationSettingsDao {
    @Query("SELECT * FROM application_settings WHERE id = :id")
    suspend fun getById(id: String): SettingsEntity?

    @Query("SELECT * FROM application_settings WHERE key = :key")
    suspend fun getByKey(key: String): SettingsEntity?

    @Query("SELECT * FROM application_settings WHERE key = :key")
    fun getByKeyFlow(key: String): Flow<SettingsEntity?>

    @Query("SELECT * FROM application_settings")
    fun getAllFlow(): Flow<List<SettingsEntity>>

    @Query("SELECT * FROM application_settings")
    suspend fun getAll(): List<SettingsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SettingsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SettingsEntity>)

    @Update
    suspend fun update(entity: SettingsEntity)

    @Delete
    suspend fun delete(entity: SettingsEntity)

    @Query("DELETE FROM application_settings WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM application_settings WHERE key = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM application_settings")
    suspend fun deleteAll()
}

package com.cereal.client.infrastructure.data.datasource.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ArtifactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtifactDao {
    @Insert
    suspend fun insert(entity: ArtifactEntity)

    @Query("SELECT * FROM artifact WHERE task_id = :taskId ORDER BY created_at ASC")
    fun observeByTaskId(taskId: String): Flow<List<ArtifactEntity>>

    @Query("SELECT * FROM artifact WHERE id = :artifactId")
    suspend fun getById(artifactId: String): ArtifactEntity?

    @Query("DELETE FROM artifact WHERE task_id = :taskId")
    suspend fun deleteByTaskId(taskId: String)
}

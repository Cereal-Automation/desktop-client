package com.cereal.client.infrastructure.data.datasource.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cereal.client.infrastructure.data.datasource.database.room.entity.RunTraceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RunTraceDao {
    @Insert
    suspend fun insert(entity: RunTraceEntity)

    @Query("SELECT * FROM run_trace")
    suspend fun getAll(): List<RunTraceEntity>

    @Query("SELECT * FROM run_trace WHERE task_id = :taskId")
    fun observeByTaskId(taskId: String): Flow<List<RunTraceEntity>>

    @Query("DELETE FROM run_trace WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM run_trace WHERE task_id = :taskId")
    suspend fun deleteByTaskId(taskId: String)
}

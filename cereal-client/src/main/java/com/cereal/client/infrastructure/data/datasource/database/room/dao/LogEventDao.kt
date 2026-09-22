package com.cereal.client.infrastructure.data.datasource.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cereal.client.infrastructure.data.datasource.database.room.entity.LogEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: LogEventEntity)

    @Query("SELECT * FROM log_event WHERE task_id = :taskId ORDER BY timestamp ASC")
    fun observeByTaskId(taskId: String): Flow<List<LogEventEntity>>

    @Query(
        """
        DELETE FROM log_event
        WHERE task_id = :taskId
        AND id NOT IN (
            SELECT id FROM log_event
            WHERE task_id = :taskId
            ORDER BY timestamp DESC
            LIMIT :keepCount
        )
        """,
    )
    suspend fun pruneExcess(
        taskId: String,
        keepCount: Int,
    )
}

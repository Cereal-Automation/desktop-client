package com.cereal.client.infrastructure.data.datasource.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cereal.client.infrastructure.data.datasource.database.room.entity.NotificationHistoryAttemptEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.NotificationHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationHistoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNotification(entity: NotificationHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttempts(entities: List<NotificationHistoryAttemptEntity>)

    @Query("SELECT * FROM notification_history WHERE task_id = :taskId ORDER BY timestamp DESC")
    fun observeByTaskId(taskId: String): Flow<List<NotificationHistoryEntity>>

    @Query("SELECT * FROM notification_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NotificationHistoryEntity>>

    @Query(
        "SELECT * FROM notification_history_attempt WHERE notification_id = :notificationId ORDER BY position ASC",
    )
    fun observeAttempts(notificationId: String): Flow<List<NotificationHistoryAttemptEntity>>

    @Query("DELETE FROM notification_history WHERE timestamp < :cutoffMillis")
    suspend fun pruneOlderThan(cutoffMillis: Long)
}

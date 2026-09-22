package com.cereal.client.domain.repository

import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.model.notification.NotificationHistory
import com.cereal.client.domain.model.notification.NotificationHistoryAttempt
import kotlinx.coroutines.flow.Flow

interface NotificationHistoryRepository {
    suspend fun record(
        taskId: String,
        title: String?,
        message: String,
        timestamp: Long,
        attempts: List<ChannelAttempt>,
    )

    fun observeByTaskId(taskId: String): Flow<List<NotificationHistory>>

    /**
     * Observes the most recent notifications across all of the current user's tasks, newest-first,
     * bounded by [limit]. Emits an empty list when no user is authenticated. Backs the global
     * Notification center.
     */
    fun observeRecent(limit: Int): Flow<List<NotificationHistory>>

    fun observeAttempts(notificationId: String): Flow<List<NotificationHistoryAttempt>>

    suspend fun pruneOlderThan(cutoffMillis: Long)
}

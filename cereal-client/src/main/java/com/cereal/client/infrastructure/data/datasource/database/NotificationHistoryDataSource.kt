package com.cereal.client.infrastructure.data.datasource.database

import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.model.notification.NotificationHistory
import com.cereal.client.domain.model.notification.NotificationHistoryAttempt
import com.cereal.client.domain.model.user.User
import kotlinx.coroutines.flow.Flow

interface NotificationHistoryDataSource {
    suspend fun record(
        user: User,
        taskId: String,
        title: String?,
        message: String,
        timestamp: Long,
        attempts: List<ChannelAttempt>,
    )

    fun observeByTaskId(
        user: User,
        taskId: String,
    ): Flow<List<NotificationHistory>>

    fun observeRecent(
        user: User,
        limit: Int,
    ): Flow<List<NotificationHistory>>

    fun observeAttempts(
        user: User,
        notificationId: String,
    ): Flow<List<NotificationHistoryAttempt>>

    suspend fun pruneOlderThan(
        user: User,
        cutoffMillis: Long,
    )
}

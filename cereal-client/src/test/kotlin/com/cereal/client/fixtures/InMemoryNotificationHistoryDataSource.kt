package com.cereal.client.fixtures

import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.model.notification.NotificationHistory
import com.cereal.client.domain.model.notification.NotificationHistoryAttempt
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.NotificationHistoryDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake of [NotificationHistoryDataSource].
 *
 * Recorded notifications and their per-channel attempts are stored in [MutableStateFlow] backed
 * maps keyed by user id, so that observers reflect writes. Notification and attempt ids are
 * generated deterministically from a monotonic counter (no randomness).
 */
class InMemoryNotificationHistoryDataSource : NotificationHistoryDataSource {
    // user id -> list of notifications (newest last)
    private val notifications = MutableStateFlow<Map<String, List<NotificationHistory>>>(emptyMap())

    // user id -> (notification id -> attempts)
    private val attempts = MutableStateFlow<Map<String, Map<String, List<NotificationHistoryAttempt>>>>(emptyMap())

    private var nextId = 0L

    private fun generateId(): String = "in-memory-${nextId++}"

    override suspend fun record(
        user: User,
        taskId: String,
        title: String?,
        message: String,
        timestamp: Long,
        attempts: List<ChannelAttempt>,
    ) {
        val notificationId = generateId()
        val notification =
            NotificationHistory(
                id = notificationId,
                taskId = taskId,
                title = title,
                message = message,
                timestamp = timestamp,
            )

        val userNotifications = notifications.value[user.id].orEmpty()
        notifications.value = notifications.value + (user.id to (userNotifications + notification))

        val attemptEntities =
            attempts.map { channelAttempt ->
                NotificationHistoryAttempt(
                    id = generateId(),
                    notificationId = notificationId,
                    channel = channelAttempt.channel,
                    status = channelAttempt.status,
                    payload = channelAttempt.payload,
                    errorMessage = channelAttempt.errorMessage,
                    timestamp = timestamp,
                )
            }

        val userAttempts = this.attempts.value[user.id].orEmpty()
        this.attempts.value =
            this.attempts.value + (user.id to (userAttempts + (notificationId to attemptEntities)))
    }

    override fun observeByTaskId(
        user: User,
        taskId: String,
    ): Flow<List<NotificationHistory>> =
        notifications.map { all ->
            all[user.id].orEmpty().filter { it.taskId == taskId }
        }

    override fun observeRecent(
        user: User,
        limit: Int,
    ): Flow<List<NotificationHistory>> =
        notifications.map { all ->
            all[user.id].orEmpty().sortedByDescending { it.timestamp }.take(limit)
        }

    override fun observeAttempts(
        user: User,
        notificationId: String,
    ): Flow<List<NotificationHistoryAttempt>> =
        attempts.map { all ->
            all[user.id]?.get(notificationId).orEmpty()
        }

    override suspend fun pruneOlderThan(
        user: User,
        cutoffMillis: Long,
    ) {
        val userNotifications = notifications.value[user.id].orEmpty()
        val (kept, removed) = userNotifications.partition { it.timestamp >= cutoffMillis }
        notifications.value = notifications.value + (user.id to kept)

        val removedIds = removed.map { it.id }.toSet()
        val userAttempts = attempts.value[user.id].orEmpty()
        attempts.value = attempts.value + (user.id to (userAttempts - removedIds))
    }
}

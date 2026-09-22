package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.model.notification.NotificationHistory
import com.cereal.client.domain.model.notification.NotificationHistoryAttempt
import com.cereal.client.domain.repository.NotificationHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * In-memory [NotificationHistoryRepository] for UI tests and the sandboxed (`mock`) flavor.
 * Keeps recorded notifications and their per-channel attempts in memory instead of the database.
 *
 * All notifications live in a single list flow so [observeRecent] can aggregate across tasks
 * exactly like the real per-user Room query does.
 */
class InMemoryNotificationHistoryRepository : NotificationHistoryRepository {
    private val notifications = MutableStateFlow<List<NotificationHistory>>(emptyList())
    private val attemptsByNotification = mutableMapOf<String, MutableStateFlow<List<NotificationHistoryAttempt>>>()

    override suspend fun record(
        taskId: String,
        title: String?,
        message: String,
        timestamp: Long,
        attempts: List<ChannelAttempt>,
    ) {
        if (attempts.isEmpty()) return
        val notificationId = UUID.randomUUID().toString()
        val history =
            NotificationHistory(
                id = notificationId,
                taskId = taskId,
                title = title,
                message = message,
                timestamp = timestamp,
            )
        notifications.value = notifications.value + history
        attemptsFlow(notificationId).value =
            attempts.map { attempt ->
                NotificationHistoryAttempt(
                    id = UUID.randomUUID().toString(),
                    notificationId = notificationId,
                    channel = attempt.channel,
                    status = attempt.status,
                    payload = attempt.payload,
                    errorMessage = attempt.errorMessage,
                    timestamp = timestamp,
                )
            }
    }

    override fun observeByTaskId(taskId: String): Flow<List<NotificationHistory>> = notifications.map { all -> all.filter { it.taskId == taskId }.sortedByDescending { it.timestamp } }

    override fun observeRecent(limit: Int): Flow<List<NotificationHistory>> = notifications.map { all -> all.sortedByDescending { it.timestamp }.take(limit) }

    override fun observeAttempts(notificationId: String): Flow<List<NotificationHistoryAttempt>> = attemptsFlow(notificationId)

    override suspend fun pruneOlderThan(cutoffMillis: Long) {
        val (kept, removed) = notifications.value.partition { it.timestamp >= cutoffMillis }
        notifications.value = kept
        removed.forEach { attemptsByNotification.remove(it.id) }
    }

    private fun attemptsFlow(notificationId: String) = attemptsByNotification.getOrPut(notificationId) { MutableStateFlow(emptyList()) }
}

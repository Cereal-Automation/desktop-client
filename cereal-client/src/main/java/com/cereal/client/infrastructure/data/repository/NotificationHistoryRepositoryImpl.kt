package com.cereal.client.infrastructure.data.repository

import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.model.notification.NotificationHistory
import com.cereal.client.domain.model.notification.NotificationHistoryAttempt
import com.cereal.client.domain.repository.NotificationHistoryRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.NotificationHistoryDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

class NotificationHistoryRepositoryImpl(
    private val dataSource: NotificationHistoryDataSource,
    private val userSession: UserSession,
) : NotificationHistoryRepository {
    private val logger = LoggerFactory.getLogger(NotificationHistoryRepositoryImpl::class.java)

    override suspend fun record(
        taskId: String,
        title: String?,
        message: String,
        timestamp: Long,
        attempts: List<ChannelAttempt>,
    ) {
        if (attempts.isEmpty()) return
        try {
            val user = userSession.requireUser()
            dataSource.record(user, taskId, title, message, timestamp, attempts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to persist notification history for task $taskId", e)
            CrashReporter.report(e)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeByTaskId(taskId: String): Flow<List<NotificationHistory>> =
        userSession.getUserFlow().flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else dataSource.observeByTaskId(user, taskId)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRecent(limit: Int): Flow<List<NotificationHistory>> =
        userSession.getUserFlow().flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else dataSource.observeRecent(user, limit)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAttempts(notificationId: String): Flow<List<NotificationHistoryAttempt>> =
        userSession.getUserFlow().flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else dataSource.observeAttempts(user, notificationId)
        }

    override suspend fun pruneOlderThan(cutoffMillis: Long) {
        try {
            // Prune runs during bootstrap, which can complete before a user is authenticated
            // (e.g. logged-out launch). There is no per-user database to prune in that case, so
            // skip silently rather than throwing/logging a spurious error.
            val user = userSession.getUserFlow().first()
            if (user == null) {
                logger.debug("Skipping notification history prune: no authenticated user.")
                return
            }
            dataSource.pruneOlderThan(user, cutoffMillis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to prune notification history older than $cutoffMillis", e)
            CrashReporter.report(e)
        }
    }
}

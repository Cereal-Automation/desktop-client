package com.cereal.client.infrastructure.data.datasource.database.room

import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.model.notification.NotificationChannelType
import com.cereal.client.domain.model.notification.NotificationHistory
import com.cereal.client.domain.model.notification.NotificationHistoryAttempt
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.NotificationHistoryDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.entity.NotificationChannel
import com.cereal.client.infrastructure.data.datasource.database.room.entity.NotificationDeliveryStatus
import com.cereal.client.infrastructure.data.datasource.database.room.entity.NotificationHistoryAttemptEntity
import com.cereal.client.infrastructure.data.datasource.database.room.entity.NotificationHistoryEntity
import com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import com.cereal.client.domain.model.notification.NotificationDeliveryStatus as DomainStatus

class RoomNotificationHistoryDataSource(
    private val roomDatabases: RoomDatabases,
) : NotificationHistoryDataSource {
    override suspend fun record(
        user: User,
        taskId: String,
        title: String?,
        message: String,
        timestamp: Long,
        attempts: List<ChannelAttempt>,
    ) {
        if (attempts.isEmpty()) return

        val userDatabase = roomDatabases.getUserDatabase(user)
        val notificationId = UUID.randomUUID().toString()
        val notification =
            NotificationHistoryEntity(
                id = notificationId,
                taskId = taskId,
                title = EncryptedString.from(title),
                message = EncryptedString.of(message),
                timestamp = timestamp,
            )
        val attemptEntities =
            attempts.mapIndexed { index, attempt ->
                NotificationHistoryAttemptEntity(
                    id = UUID.randomUUID().toString(),
                    notificationId = notificationId,
                    channel = attempt.channel.toRoom(),
                    status = attempt.status.toRoom(),
                    payload = EncryptedString.from(attempt.payload),
                    errorMessage = EncryptedString.from(attempt.errorMessage),
                    timestamp = timestamp,
                    position = index,
                )
            }

        userDatabase.immediateWriteTransaction {
            userDatabase.notificationHistoryDao().insertNotification(notification)
            userDatabase.notificationHistoryDao().insertAttempts(attemptEntities)
        }
    }

    override fun observeByTaskId(
        user: User,
        taskId: String,
    ): Flow<List<NotificationHistory>> =
        roomDatabases
            .getUserDatabase(user)
            .notificationHistoryDao()
            .observeByTaskId(taskId)
            .map { entities -> entities.map { it.toDomain() } }

    override fun observeRecent(
        user: User,
        limit: Int,
    ): Flow<List<NotificationHistory>> =
        roomDatabases
            .getUserDatabase(user)
            .notificationHistoryDao()
            .observeRecent(limit)
            .map { entities -> entities.map { it.toDomain() } }

    override fun observeAttempts(
        user: User,
        notificationId: String,
    ): Flow<List<NotificationHistoryAttempt>> =
        roomDatabases
            .getUserDatabase(user)
            .notificationHistoryDao()
            .observeAttempts(notificationId)
            .map { entities -> entities.map { it.toDomain() } }

    override suspend fun pruneOlderThan(
        user: User,
        cutoffMillis: Long,
    ) {
        roomDatabases
            .getUserDatabase(user)
            .notificationHistoryDao()
            .pruneOlderThan(cutoffMillis)
    }

    private fun NotificationHistoryEntity.toDomain() =
        NotificationHistory(
            id = id,
            taskId = taskId,
            title = title?.value,
            message = message.value.orEmpty(),
            timestamp = timestamp,
        )

    private fun NotificationHistoryAttemptEntity.toDomain() =
        NotificationHistoryAttempt(
            id = id,
            notificationId = notificationId,
            channel = channel.toDomain(),
            status = status.toDomain(),
            payload = payload?.value,
            errorMessage = errorMessage?.value,
            timestamp = timestamp,
        )

    private fun NotificationChannelType.toRoom(): NotificationChannel =
        when (this) {
            NotificationChannelType.DISCORD -> NotificationChannel.DISCORD
            NotificationChannelType.TELEGRAM -> NotificationChannel.TELEGRAM
            NotificationChannelType.EMAIL -> NotificationChannel.EMAIL
            NotificationChannelType.SYSTEM -> NotificationChannel.SYSTEM
        }

    private fun NotificationChannel.toDomain(): NotificationChannelType =
        when (this) {
            NotificationChannel.DISCORD -> NotificationChannelType.DISCORD
            NotificationChannel.TELEGRAM -> NotificationChannelType.TELEGRAM
            NotificationChannel.EMAIL -> NotificationChannelType.EMAIL
            NotificationChannel.SYSTEM -> NotificationChannelType.SYSTEM
        }

    private fun DomainStatus.toRoom(): NotificationDeliveryStatus =
        when (this) {
            DomainStatus.SUCCESS -> NotificationDeliveryStatus.SUCCESS
            DomainStatus.FAILURE -> NotificationDeliveryStatus.FAILURE
        }

    private fun NotificationDeliveryStatus.toDomain(): DomainStatus =
        when (this) {
            NotificationDeliveryStatus.SUCCESS -> DomainStatus.SUCCESS
            NotificationDeliveryStatus.FAILURE -> DomainStatus.FAILURE
        }
}

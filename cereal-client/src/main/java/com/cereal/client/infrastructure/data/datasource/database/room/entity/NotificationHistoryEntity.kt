package com.cereal.client.infrastructure.data.datasource.database.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString

enum class NotificationChannel {
    DISCORD,
    TELEGRAM,
    EMAIL,
    SYSTEM,
}

enum class NotificationDeliveryStatus {
    SUCCESS,
    FAILURE,
}

@Entity(
    tableName = "notification_history",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["task_id"]),
        Index(value = ["timestamp"]),
    ],
)
data class NotificationHistoryEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "task_id")
    val taskId: String,
    val title: EncryptedString?,
    val message: EncryptedString,
    val timestamp: Long,
)

@Entity(
    tableName = "notification_history_attempt",
    foreignKeys = [
        ForeignKey(
            entity = NotificationHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["notification_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["notification_id"]),
    ],
)
data class NotificationHistoryAttemptEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "notification_id")
    val notificationId: String,
    val channel: NotificationChannel,
    val status: NotificationDeliveryStatus,
    val payload: EncryptedString?,
    @ColumnInfo(name = "error_message")
    val errorMessage: EncryptedString?,
    val timestamp: Long,
    /**
     * Zero-based ordinal of this attempt within its parent notification, used to render attempts in
     * a stable send order (all attempts share the same [timestamp], so timestamp alone is not a
     * deterministic sort key).
     */
    val position: Int,
)

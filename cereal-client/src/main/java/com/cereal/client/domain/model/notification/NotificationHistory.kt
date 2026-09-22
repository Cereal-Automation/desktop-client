package com.cereal.client.domain.model.notification

/**
 * Domain model for a persisted notification sent from a script instance.
 */
data class NotificationHistory(
    val id: String,
    val taskId: String,
    val title: String?,
    val message: String,
    val timestamp: Long,
)

/**
 * Domain model for a per-channel delivery attempt of a [NotificationHistory].
 */
data class NotificationHistoryAttempt(
    val id: String,
    val notificationId: String,
    val channel: NotificationChannelType,
    val status: NotificationDeliveryStatus,
    val payload: String?,
    val errorMessage: String?,
    val timestamp: Long,
)

enum class NotificationChannelType {
    DISCORD,
    TELEGRAM,
    EMAIL,
    SYSTEM,
}

enum class NotificationDeliveryStatus {
    SUCCESS,
    FAILURE,
}

/**
 * Captured outcome of a single channel send, used when recording a notification.
 */
data class ChannelAttempt(
    val channel: NotificationChannelType,
    val status: NotificationDeliveryStatus,
    val payload: String?,
    val errorMessage: String?,
)

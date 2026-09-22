package com.cereal.client.presentation.notification

import androidx.compose.runtime.Immutable
import com.cereal.client.domain.model.notification.NotificationChannelType

/**
 * A single notification row in the Notification center. A flat, message-only view of a
 * `NotificationHistory`; delivery detail is loaded separately when the row is expanded.
 */
@Immutable
data class NotificationCenterUiModel(
    val id: String,
    val taskId: String,
    val taskName: String,
    val title: String?,
    val message: String,
    val relativeTime: String,
    val unseen: Boolean,
)

/** A single per-channel delivery attempt, shown in the expanded delivery panel of a row. */
@Immutable
data class NotificationDeliveryUiModel(
    val id: String,
    val channel: NotificationChannelType,
    val delivered: Boolean,
    val errorMessage: String?,
)

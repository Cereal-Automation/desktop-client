package com.cereal.client.domain.model.notification

/**
 * Domain model representing system (OS-level) notification data.
 */
data class SystemNotificationData(
    val title: String?,
    val message: String,
) : Notification()

package com.cereal.client.infrastructure.data.notification

import com.cereal.client.domain.model.notification.NotificationStrategy
import com.cereal.client.domain.model.notification.SystemNotificationData
import com.cereal.client.infrastructure.data.datasource.os.NotificationDataSource

/**
 * Strategy for sending system (desktop) notifications.
 */
class SystemNotificationStrategy(
    private val notificationDataSource: NotificationDataSource,
) : NotificationStrategy<SystemNotificationData> {
    override suspend fun send(data: SystemNotificationData) {
        notificationDataSource.sendNotification(data.title, data.message)
    }
}

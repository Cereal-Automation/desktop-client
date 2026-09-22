package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.model.notification.Notification
import com.cereal.client.domain.provider.NotificationProvider

/** No-op [NotificationProvider] that records the notifications it was asked to send. */
class InMemoryNotificationProvider : NotificationProvider {
    val sent = mutableListOf<Notification>()

    override suspend fun sendNotification(notification: Notification) {
        sent += notification
    }
}

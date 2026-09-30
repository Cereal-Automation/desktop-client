package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.model.notification.Notification
import com.cereal.client.domain.provider.NotificationProvider

/**
 * No-op [NotificationProvider] that records the notifications it was asked to send. Set [failure]
 * to make a send throw it (after recording), as a broken webhook or SMTP login would.
 */
class InMemoryNotificationProvider : NotificationProvider {
    val sent = mutableListOf<Notification>()

    @Volatile
    var failure: ((Notification) -> Exception?) = { null }

    override suspend fun sendNotification(notification: Notification) {
        sent += notification
        failure(notification)?.let { throw it }
    }
}

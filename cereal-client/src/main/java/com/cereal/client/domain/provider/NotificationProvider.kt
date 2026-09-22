package com.cereal.client.domain.provider

import com.cereal.client.domain.model.notification.Notification

interface NotificationProvider {
    suspend fun sendNotification(
        notification: Notification,
    )
}

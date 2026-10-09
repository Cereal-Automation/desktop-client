package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.notification.DiscordNotificationData
import com.cereal.client.domain.model.notification.EmailNotificationData
import com.cereal.client.domain.model.notification.Notification
import com.cereal.client.domain.model.notification.SystemNotificationData
import com.cereal.client.domain.model.notification.TelegramNotificationData
import com.cereal.client.domain.provider.NotificationProvider
import com.cereal.client.infrastructure.data.notification.DiscordNotificationStrategy
import com.cereal.client.infrastructure.data.notification.EmailNotificationStrategy
import com.cereal.client.infrastructure.data.notification.SystemNotificationStrategy
import com.cereal.client.infrastructure.data.notification.TelegramNotificationStrategy

/**
 * Implementation of NotificationProvider that manages multiple notification channels.
 */

class NotificationProviderImpl(
    private val systemStrategy: SystemNotificationStrategy,
    private val discordStrategy: DiscordNotificationStrategy,
    private val telegramStrategy: TelegramNotificationStrategy,
    private val emailStrategy: EmailNotificationStrategy,
) : NotificationProvider {
    override suspend fun sendNotification(
        notification: Notification,
    ) {
        // Delivery failures propagate: callers record them (notification history) or surface them (send test).
        when (notification) {
            is SystemNotificationData -> systemStrategy.send(notification)
            is DiscordNotificationData -> discordStrategy.send(notification)
            is TelegramNotificationData -> telegramStrategy.send(notification)
            is EmailNotificationData -> emailStrategy.send(notification)
        }
    }
}

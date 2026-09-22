package com.cereal.client.infrastructure.provider

import com.cereal.client.application.exception.CrashReporter
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
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

/**
 * Implementation of NotificationProvider that manages multiple notification channels.
 */

class NotificationProviderImpl(
    private val systemStrategy: SystemNotificationStrategy,
    private val discordStrategy: DiscordNotificationStrategy,
    private val telegramStrategy: TelegramNotificationStrategy,
    private val emailStrategy: EmailNotificationStrategy,
) : NotificationProvider {
    private val logger = LoggerFactory.getLogger(NotificationProviderImpl::class.java)

    override suspend fun sendNotification(
        notification: Notification,
    ) {
        try {
            when (notification) {
                is SystemNotificationData -> systemStrategy.send(notification)
                is DiscordNotificationData -> discordStrategy.send(notification)
                is TelegramNotificationData -> telegramStrategy.send(notification)
                is EmailNotificationData -> emailStrategy.send(notification)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            logger.error("Failed to send notification: $notification", e)
            CrashReporter.report(e)
        }
    }
}

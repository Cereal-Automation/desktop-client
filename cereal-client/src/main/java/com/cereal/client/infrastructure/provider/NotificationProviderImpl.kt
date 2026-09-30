package com.cereal.client.infrastructure.provider

import com.cereal.client.application.exception.CerealException
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
        } catch (e: CerealException) {
            throw failed(notification, e)
        } catch (e: RuntimeException) {
            CrashReporter.report(e)
            throw failed(notification, e)
        } catch (e: Exception) {
            throw failed(notification, e)
        }
    }

    /**
     * Logs [e] and returns it as a [CerealException] to rethrow, so callers see the failure: history records it
     * per channel and a test message reports it. A broken webhook or SMTP login is the user's setup, not a bug
     * for Sentry. Only the channel is logged: the notification carries the webhook URL, bot token or SMTP password.
     */
    private fun failed(
        notification: Notification,
        e: Exception,
    ): CerealException {
        logger.warn("Failed to send {} notification", notification::class.simpleName, e)
        return e as? CerealException ?: CerealException(e.message ?: "The notification could not be sent.", e)
    }
}

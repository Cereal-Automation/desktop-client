package com.cereal.client.infrastructure.data.notification

import com.cereal.client.domain.model.notification.DiscordNotificationData
import com.cereal.client.domain.model.notification.NotificationStrategy
import com.cereal.client.infrastructure.data.notification.discord.DiscordHttpClient
import com.cereal.client.infrastructure.data.notification.mapper.toSdkDiscordMessage

/**
 * Strategy for sending Discord notifications.
 */
class DiscordNotificationStrategy(
    private val discordHttpClient: DiscordHttpClient,
) : NotificationStrategy<DiscordNotificationData> {
    override suspend fun send(data: DiscordNotificationData) {
        val discordWebhookUrl = data.webhookUrl

        discordHttpClient.message(
            discordWebhookUrl,
            data.toSdkDiscordMessage(),
        )
    }
}

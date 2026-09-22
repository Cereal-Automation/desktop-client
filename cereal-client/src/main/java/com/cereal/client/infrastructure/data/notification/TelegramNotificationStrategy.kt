package com.cereal.client.infrastructure.data.notification

import com.cereal.client.domain.model.notification.NotificationStrategy
import com.cereal.client.domain.model.notification.TelegramNotificationData
import com.cereal.client.infrastructure.data.notification.mapper.toSdkTelegramMessage
import com.cereal.client.infrastructure.data.notification.telegram.TelegramHttpClient

/**
 * Strategy for sending Telegram notifications.
 */
class TelegramNotificationStrategy(
    private val telegramHttpClient: TelegramHttpClient,
) : NotificationStrategy<TelegramNotificationData> {
    override suspend fun send(data: TelegramNotificationData) {
        val botToken = data.botToken
        val chatId = data.chatId

        telegramHttpClient.sendMessage(
            botToken,
            data.toSdkTelegramMessage(),
        )
    }
}

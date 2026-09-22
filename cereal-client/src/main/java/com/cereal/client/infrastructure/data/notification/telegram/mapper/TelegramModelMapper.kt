package com.cereal.client.infrastructure.data.notification.telegram.mapper

import com.cereal.client.infrastructure.data.notification.telegram.model.SerializableTelegramMessage
import com.cereal.sdk.component.notification.telegram.model.TelegramMessage

/**
 * Mapper for converting Telegram SDK models to serializable models for JSON serialization.
 */
object TelegramModelMapper {
    fun toSerializable(message: TelegramMessage): SerializableTelegramMessage =
        SerializableTelegramMessage(
            chatId = message.chatId,
            text = message.text,
            parseMode =
                TelegramParseModeMapper.toApiString(
                    message.parseMode,
                ),
            disableWebPagePreview = message.disableWebPagePreview,
            disableNotification = message.disableNotification,
            replyToMessageId = message.replyToMessageId,
        )
}

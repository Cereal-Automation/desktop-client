package com.cereal.client.infrastructure.data.notification.telegram.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable version of TelegramMessage for JSON serialization.
 * This mirrors the SDK TelegramMessage but is specifically designed for kotlinx serialization.
 */
@Serializable
data class SerializableTelegramMessage(
    @SerialName("chat_id") val chatId: String? = null,
    val text: String? = null,
    @SerialName("parse_mode") val parseMode: String? = null,
    @SerialName("disable_web_page_preview") val disableWebPagePreview: Boolean? = null,
    @SerialName("disable_notification") val disableNotification: Boolean? = null,
    @SerialName("reply_to_message_id") val replyToMessageId: Int? = null,
)

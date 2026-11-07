package com.cereal.client.infrastructure.data.notification.telegram.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The parts of a Bot API update remote approval reads: a button press. */
@Serializable
data class TelegramUpdate(
    @SerialName("update_id") val updateId: Long,
    @SerialName("callback_query") val callbackQuery: CallbackQuery? = null,
) {
    @Serializable
    data class CallbackQuery(
        val id: String,
        val from: User,
        val message: Message? = null,
        val data: String? = null,
    )

    @Serializable
    data class User(
        val id: Long,
    )

    @Serializable
    data class Chat(
        val id: Long,
    )

    @Serializable
    data class Message(
        @SerialName("message_id") val messageId: Long,
        val chat: Chat,
        val text: String? = null,
    )
}

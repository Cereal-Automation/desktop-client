package com.cereal.client.domain.model.notification

/**
 * Domain model representing Telegram-specific notification data.
 */
data class TelegramNotificationData(
    val chatId: String,
    val text: String,
    val parseMode: TelegramParseMode? = null,
    val disableWebPagePreview: Boolean? = null,
    val disableNotification: Boolean? = null,
    val replyToMessageId: Int? = null,
    val botToken: String,
) : Notification() {
    init {
        // Validate chatId format
        require(chatId.isNotBlank()) { "chatId cannot be blank" }
        require(chatId.length <= MAX_CHAT_ID_LENGTH) { "chatId cannot exceed $MAX_CHAT_ID_LENGTH characters" }

        // Validate text content
        require(text.isNotBlank()) { "text cannot be blank" }
        require(text.length <= MAX_TEXT_LENGTH) { "text cannot exceed $MAX_TEXT_LENGTH characters (Telegram limit)" }

        // Validate botToken format
        require(botToken.isNotBlank()) { "botToken cannot be blank" }
        require(botToken.matches(Regex("^[0-9]+:[A-Za-z0-9_-]+$"))) {
            "botToken must follow Telegram bot token format (number:alphanumeric)"
        }

        // Validate replyToMessageId is positive if provided
        replyToMessageId?.let {
            require(it > 0) { "replyToMessageId must be a positive integer" }
        }
    }

    private companion object {
        private const val MAX_CHAT_ID_LENGTH = 100
        private const val MAX_TEXT_LENGTH = 4096
    }
}

enum class TelegramParseMode {
    MARKDOWN,
    MARKDOWN_V2,
    HTML,
}

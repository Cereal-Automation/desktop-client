package com.cereal.client.infrastructure.data.notification.telegram.mapper

import com.cereal.sdk.component.notification.telegram.model.TelegramParseMode

/**
 * Mapper for converting TelegramParseMode enum to the string format expected by Telegram Bot API.
 */
object TelegramParseModeMapper {
    /**
     * Converts TelegramParseMode enum to the string format expected by Telegram API.
     *
     * @param parseMode The parse mode enum value
     * @return The string representation for Telegram API, or null if parseMode is null
     */
    fun toApiString(parseMode: TelegramParseMode?): String? =
        when (parseMode) {
            TelegramParseMode.HTML -> "HTML"
            TelegramParseMode.MARKDOWN -> "Markdown"
            TelegramParseMode.MARKDOWN_V2 -> "MarkdownV2"
            null -> null
        }

    /**
     * Converts a string from Telegram API to TelegramParseMode enum.
     *
     * @param value The string value from Telegram API
     * @return The corresponding TelegramParseMode enum, or null if not recognized
     */
    fun fromApiString(value: String?): TelegramParseMode? =
        when (value) {
            "HTML" -> TelegramParseMode.HTML
            "Markdown" -> TelegramParseMode.MARKDOWN
            "MarkdownV2" -> TelegramParseMode.MARKDOWN_V2
            else -> null
        }
}

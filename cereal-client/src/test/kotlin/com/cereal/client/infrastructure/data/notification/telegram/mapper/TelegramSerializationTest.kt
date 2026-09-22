package com.cereal.client.infrastructure.data.notification.telegram.mapper

import com.cereal.client.infrastructure.data.notification.telegram.mapper.TelegramModelMapper
import com.cereal.sdk.component.notification.telegram.model.TelegramMessage
import com.cereal.sdk.component.notification.telegram.model.TelegramParseMode
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class TelegramSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `test TelegramMessage serialization with basic fields`() {
        // Given
        val sdkMessage =
            TelegramMessage(
                chatId = "123456789",
                text = "Hello, World!",
                parseMode = TelegramParseMode.HTML,
                disableWebPagePreview = true,
                disableNotification = false,
                botToken = "bot123456789:ABCdefGHIjklMNOpqrsTUVwxyz",
            )

        // When
        val serializableMessage = TelegramModelMapper.toSerializable(sdkMessage)
        val jsonString = json.encodeToString(serializableMessage)

        // Then
        assertTrue(jsonString.contains("\"chat_id\":\"123456789\""))
        assertTrue(jsonString.contains("\"text\":\"Hello, World!\""))
        assertTrue(jsonString.contains("\"parse_mode\":\"HTML\""))
        assertTrue(jsonString.contains("\"disable_web_page_preview\":true"))
        assertTrue(jsonString.contains("\"disable_notification\":false"))
    }

    @Test
    fun `test TelegramMessage serialization with minimal fields`() {
        // Given
        val sdkMessage = TelegramMessage(text = "Simple message")

        // When
        val serializableMessage = TelegramModelMapper.toSerializable(sdkMessage)
        val jsonString = json.encodeToString(serializableMessage)

        // Then
        assertTrue(jsonString.contains("\"text\":\"Simple message\""))
    }
}

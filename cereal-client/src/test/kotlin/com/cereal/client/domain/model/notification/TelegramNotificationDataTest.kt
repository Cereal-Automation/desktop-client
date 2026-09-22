package com.cereal.client.domain.model.notification

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TelegramNotificationDataTest {
    private val validChatId = "123456789"
    private val validBotToken = "123456789:ABCdefGHIjklMNOpqrsTUVwxyz"

    @Test
    fun `should create valid TelegramNotificationData with all fields`() {
        assertDoesNotThrow {
            TelegramNotificationData(
                chatId = validChatId,
                text = "Test message",
                parseMode = TelegramParseMode.MARKDOWN,
                disableWebPagePreview = true,
                disableNotification = false,
                replyToMessageId = 1,
                botToken = validBotToken,
            )
        }
    }

    @Test
    fun `should create valid TelegramNotificationData with only text`() {
        val data =
            assertDoesNotThrow {
                TelegramNotificationData(
                    text = "Test message",
                    chatId = validChatId,
                    botToken = validBotToken,
                )
            }
        assertNotNull(data)
        assertEquals("Test message", data.text)
    }

    @Test
    fun `should throw exception when chatId is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                TelegramNotificationData(chatId = "   ", text = "Test", botToken = validBotToken)
            }
        assertEquals("chatId cannot be blank", exception.message)
    }

    @Test
    fun `should throw exception when chatId exceeds 100 characters`() {
        val longChatId = "a".repeat(101)
        val exception =
            assertThrows<IllegalArgumentException> {
                TelegramNotificationData(chatId = longChatId, text = "Test", botToken = validBotToken)
            }
        assertEquals("chatId cannot exceed 100 characters", exception.message)
    }

    @Test
    fun `should allow chatId with exactly 100 characters`() {
        val chatId = "a".repeat(100)
        assertDoesNotThrow {
            TelegramNotificationData(chatId = chatId, text = "Test", botToken = validBotToken)
        }
    }

    @Test
    fun `should throw exception when text is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                TelegramNotificationData(chatId = validChatId, text = "   ", botToken = validBotToken)
            }
        assertEquals("text cannot be blank", exception.message)
    }

    @Test
    fun `should throw exception when text exceeds 4096 characters`() {
        val longText = "a".repeat(4097)
        val exception =
            assertThrows<IllegalArgumentException> {
                TelegramNotificationData(chatId = validChatId, text = longText, botToken = validBotToken)
            }
        assertEquals("text cannot exceed 4096 characters (Telegram limit)", exception.message)
    }

    @Test
    fun `should allow text with exactly 4096 characters`() {
        val text = "a".repeat(4096)
        assertDoesNotThrow {
            TelegramNotificationData(chatId = validChatId, text = text, botToken = validBotToken)
        }
    }

    @Test
    fun `should throw exception when botToken is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                TelegramNotificationData(text = "Test", chatId = validChatId, botToken = "   ")
            }
        assertEquals("botToken cannot be blank", exception.message)
    }

    @Test
    fun `should throw exception when botToken has invalid format`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                TelegramNotificationData(text = "Test", chatId = validChatId, botToken = "invalid_token")
            }
        assertEquals("botToken must follow Telegram bot token format (number:alphanumeric)", exception.message)
    }

    @Test
    fun `should accept valid botToken formats`() {
        val validTokens =
            listOf(
                "123456789:ABCdefGHIjklMNOpqrsTUVwxyz",
                "987654321:abcDEF123-_xyz",
                "1:a",
                "123:ABC-_123",
            )

        validTokens.forEach { token ->
            assertDoesNotThrow("Token '$token' should be valid") {
                TelegramNotificationData(text = "Test", chatId = validChatId, botToken = token)
            }
        }
    }

    @Test
    fun `should reject invalid botToken formats`() {
        val invalidTokens =
            listOf(
                "no_colon",
                ":missing_number",
                "123:",
                "abc:123",
                "123:abc@def",
                "123:abc def",
                "123:abc#def",
            )

        invalidTokens.forEach { token ->
            assertThrows<IllegalArgumentException>("Token '$token' should be invalid") {
                TelegramNotificationData(text = "Test", chatId = validChatId, botToken = token)
            }
        }
    }

    @Test
    fun `should throw exception when replyToMessageId is zero`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                TelegramNotificationData(text = "Test", chatId = validChatId, botToken = validBotToken, replyToMessageId = 0)
            }
        assertEquals("replyToMessageId must be a positive integer", exception.message)
    }

    @Test
    fun `should throw exception when replyToMessageId is negative`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                TelegramNotificationData(text = "Test", chatId = validChatId, botToken = validBotToken, replyToMessageId = -1)
            }
        assertEquals("replyToMessageId must be a positive integer", exception.message)
    }

    @Test
    fun `should accept positive replyToMessageId`() {
        assertDoesNotThrow {
            TelegramNotificationData(text = "Test", chatId = validChatId, botToken = validBotToken, replyToMessageId = 1)
        }
        assertDoesNotThrow {
            TelegramNotificationData(text = "Test", chatId = validChatId, botToken = validBotToken, replyToMessageId = 999999)
        }
    }

    @Test
    fun `should work with all TelegramParseMode values`() {
        TelegramParseMode.values().forEach { mode ->
            assertDoesNotThrow("ParseMode '$mode' should be valid") {
                TelegramNotificationData(
                    chatId = validChatId,
                    botToken = validBotToken,
                    text = "Test",
                    parseMode = mode,
                )
            }
        }
    }
}

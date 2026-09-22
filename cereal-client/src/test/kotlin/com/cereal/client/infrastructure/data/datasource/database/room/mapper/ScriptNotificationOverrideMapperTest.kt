package com.cereal.client.infrastructure.data.datasource.database.room.mapper

import com.cereal.client.domain.model.notification.DiscordOverrides
import com.cereal.client.domain.model.notification.EmailOverrides
import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.domain.model.notification.TelegramOverrides
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptNotificationOverrideEntity
import com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Pure round-trip unit test for [ScriptNotificationOverrideMapper] (the "Infra: mappers" boundary in
 * AGENTS.md — no database, no framework).
 */
class ScriptNotificationOverrideMapperTest {
    private val mapper = ScriptNotificationOverrideMapper()
    private val packageId = UUID.randomUUID().toString()

    @Test
    fun `toEntity maps every override field`() {
        val overrides =
            ScriptNotificationOverrides(
                discordOverrides = DiscordOverrides(webhookUrl = "https://discord/webhook"),
                telegramOverrides = TelegramOverrides(botToken = "bot", chatId = "chat"),
                emailOverrides =
                    EmailOverrides(
                        smtpHost = "smtp.example.com",
                        smtpPort = 587,
                        username = "user",
                        password = "secret",
                        from = "from@example.com",
                        to = "to@example.com",
                        useTls = false,
                    ),
            )

        val entity = mapper.toEntity(packageId, overrides)

        assertEquals(UUID.fromString(packageId), entity.packageId)
        assertEquals("https://discord/webhook", entity.discordWebhookUrl?.value)
        assertEquals("bot", entity.telegramBotToken?.value)
        assertEquals("chat", entity.telegramChatId?.value)
        assertEquals("smtp.example.com", entity.emailSmtpHost?.value)
        assertEquals(587, entity.emailSmtpPort)
        assertEquals("user", entity.emailUsername?.value)
        assertEquals("secret", entity.emailPassword?.value)
        assertEquals("from@example.com", entity.emailFrom?.value)
        assertEquals("to@example.com", entity.emailTo?.value)
        assertEquals(false, entity.emailUseTls)
    }

    @Test
    fun `full overrides round-trip back to an equal domain model`() {
        val overrides =
            ScriptNotificationOverrides(
                discordOverrides = DiscordOverrides(webhookUrl = "https://discord/webhook"),
                telegramOverrides = TelegramOverrides(botToken = "bot", chatId = "chat"),
                emailOverrides =
                    EmailOverrides(
                        smtpHost = "smtp.example.com",
                        smtpPort = 587,
                        username = "user",
                        password = "secret",
                        from = "from@example.com",
                        to = "to@example.com",
                        useTls = true,
                    ),
            )

        val roundTripped = mapper.toDomain(mapper.toEntity(packageId, overrides))

        assertEquals(overrides, roundTripped)
    }

    @Test
    fun `null entity maps to null`() {
        assertNull(mapper.toDomain(null))
    }

    @Test
    fun `entity with no configured overrides maps to null`() {
        assertNull(mapper.toDomain(emptyEntity()))
    }

    @Test
    fun `blank values are treated as not configured`() {
        val entity =
            emptyEntity().copy(
                discordWebhookUrl = EncryptedString("   "),
                telegramBotToken = EncryptedString(""),
                telegramChatId = EncryptedString(""),
            )

        assertNull(mapper.toDomain(entity))
    }

    @Test
    fun `discord-only entity maps to discord-only domain`() {
        val entity = emptyEntity().copy(discordWebhookUrl = EncryptedString("https://discord/webhook"))

        val domain = mapper.toDomain(entity)

        assertEquals(DiscordOverrides("https://discord/webhook"), domain?.discordOverrides)
        assertNull(domain?.telegramOverrides)
        assertNull(domain?.emailOverrides)
    }

    @Test
    fun `telegram requires both token and chat id`() {
        val onlyToken = emptyEntity().copy(telegramBotToken = EncryptedString("bot"))

        val domain = mapper.toDomain(onlyToken)

        assertNull(domain) // bot token alone is not a usable override
    }

    @Test
    fun `email requires credentials, addresses and a port`() {
        // Missing port -> not a usable email override.
        val missingPort =
            emptyEntity().copy(
                emailSmtpHost = EncryptedString("smtp"),
                emailUsername = EncryptedString("user"),
                emailPassword = EncryptedString("pass"),
                emailFrom = EncryptedString("from"),
                emailTo = EncryptedString("to"),
                emailSmtpPort = null,
            )

        assertNull(mapper.toDomain(missingPort))
    }

    @Test
    fun `email useTls defaults to true when not stored`() {
        val entity =
            emptyEntity().copy(
                emailSmtpHost = EncryptedString("smtp"),
                emailSmtpPort = 25,
                emailUsername = EncryptedString("user"),
                emailPassword = EncryptedString("pass"),
                emailFrom = EncryptedString("from"),
                emailTo = EncryptedString("to"),
                emailUseTls = null,
            )

        assertEquals(true, mapper.toDomain(entity)?.emailOverrides?.useTls)
    }

    @OptIn(ExperimentalTime::class)
    private fun emptyEntity() =
        ScriptNotificationOverrideEntity(
            id = UUID.randomUUID(),
            packageId = UUID.fromString(packageId),
            discordWebhookUrl = null,
            telegramBotToken = null,
            telegramChatId = null,
            emailSmtpHost = null,
            emailSmtpPort = null,
            emailUsername = null,
            emailPassword = null,
            emailFrom = null,
            emailTo = null,
            emailUseTls = null,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        )
}

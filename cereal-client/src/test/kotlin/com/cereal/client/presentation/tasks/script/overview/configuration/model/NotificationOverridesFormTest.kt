package com.cereal.client.presentation.tasks.script.overview.configuration.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationOverridesFormTest {
    private val validWebhook = "https://discord.com/api/webhooks/123/token"

    @Test
    fun `a fresh form has nothing enabled and produces no overrides`() {
        val form = NotificationOverridesForm()

        assertFalse(form.hasAnyOverridesEnabled())
        assertTrue(form.validate())
        assertNull(form.toOverrides())
    }

    @Test
    fun `enabling discord with a valid webhook validates and produces an override`() {
        val form = NotificationOverridesForm()
        form.discordOverrideEnabled.value = true
        form.discordWebhookUrl.onValueChange(validWebhook)

        assertTrue(form.hasAnyOverridesEnabled())
        assertTrue(form.validate())

        val overrides = form.toOverrides()
        assertNotNull(overrides)
        assertEquals(validWebhook, overrides!!.discordOverrides?.webhookUrl)
        assertNull(overrides.telegramOverrides)
        assertNull(overrides.emailOverrides)
    }

    @Test
    fun `enabling discord with an invalid webhook fails validation`() {
        val form = NotificationOverridesForm()
        form.discordOverrideEnabled.value = true
        form.discordWebhookUrl.onValueChange("not-a-webhook")

        assertFalse(form.validate())
    }

    @Test
    fun `enabling telegram requires both token and chat id`() {
        val form = NotificationOverridesForm()
        form.telegramOverrideEnabled.value = true
        form.telegramBotToken.onValueChange("123456789:ABCdefToken")
        form.telegramChatId.onValueChange("-1001234567890")

        assertTrue(form.validate())

        val overrides = form.toOverrides()
        assertNotNull(overrides)
        assertEquals("123456789:ABCdefToken", overrides!!.telegramOverrides?.botToken)
        assertEquals("-1001234567890", overrides.telegramOverrides?.chatId)
    }

    @Test
    fun `enabling email builds an override from all fields`() {
        val form = NotificationOverridesForm()
        form.emailOverrideEnabled.value = true
        form.emailSmtpHost.onValueChange("smtp.example.com")
        form.emailSmtpPort.onValueChange("587")
        form.emailUsername.onValueChange("user")
        form.emailPassword.onValueChange("secret")
        form.emailFrom.onValueChange("from@example.com")
        form.emailTo.onValueChange("to@example.com")
        form.emailUseTls.value = true

        assertTrue(form.validate())

        val overrides = form.toOverrides()
        assertNotNull(overrides)
        val email = overrides!!.emailOverrides
        assertNotNull(email)
        assertEquals("smtp.example.com", email!!.smtpHost)
        assertEquals(587, email.smtpPort)
        assertTrue(email.useTls)
    }

    @Test
    fun `email override is dropped when fields are incomplete even if enabled`() {
        val form = NotificationOverridesForm()
        form.emailOverrideEnabled.value = true
        // Missing host/username/etc. — validateEmailFields() fails, so no email override is built.
        form.emailSmtpPort.onValueChange("587")

        assertNull(form.toOverrides())
    }
}

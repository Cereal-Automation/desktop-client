package com.cereal.client.infrastructure.data.datasource.preference

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApplicationPreferenceKeyTest {
    @Test
    fun `UserAuthenticationToken key is in sensitiveKeys`() {
        assertTrue(ApplicationPreferenceKey.UserAuthenticationToken.key in ApplicationPreferenceKey.sensitiveKeys)
    }

    @Test
    fun `TelegramBotToken key is in sensitiveKeys`() {
        assertTrue(ApplicationPreferenceKey.TelegramBotToken.key in ApplicationPreferenceKey.sensitiveKeys)
    }

    @Test
    fun `EmailPassword key is in sensitiveKeys`() {
        assertTrue(ApplicationPreferenceKey.EmailPassword.key in ApplicationPreferenceKey.sensitiveKeys)
    }

    @Test
    fun `DiscordWebhookUrl key is in sensitiveKeys`() {
        assertTrue(ApplicationPreferenceKey.DiscordWebhookUrl.key in ApplicationPreferenceKey.sensitiveKeys)
    }

    @Test
    fun `non-secret preferences are not in sensitiveKeys`() {
        val sensitiveKeys = ApplicationPreferenceKey.sensitiveKeys
        assertFalse(ApplicationPreferenceKey.ShowDebugLogs.key in sensitiveKeys)
        assertFalse(ApplicationPreferenceKey.DesktopNotificationsEnabled.key in sensitiveKeys)
        assertFalse(ApplicationPreferenceKey.TelegramChatId.key in sensitiveKeys)
        assertFalse(ApplicationPreferenceKey.EmailUsername.key in sensitiveKeys)
        assertFalse(ApplicationPreferenceKey.EmailSmtpHost.key in sensitiveKeys)
    }
}

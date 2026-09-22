package com.cereal.client.application.interactor.settings

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.settings.ProxyHealthCheckInterval
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Direct, deterministic coverage for the combined settings flow. The interactor was previously
 * only exercised indirectly via ApplicationSettingsViewModelTest, whose async flow collection did
 * not cover the combine lambdas reliably across environments.
 */
class GetApplicationPreferencesSettingsInteractorTest {
    @Test
    fun `run emits the repository default settings`() =
        runTest {
            val appPreferences = InMemoryApplicationPreferenceRepository()
            val notificationSettings = InMemoryNotificationSettingsRepository()
            val interactor = GetApplicationPreferencesSettingsInteractor(appPreferences, notificationSettings)

            val settings = interactor.run(Interactor.None()).first()

            assertTrue(settings.desktopNotificationsEnabled)
            assertFalse(settings.developmentScriptsEnabled)
            assertTrue(settings.discordActivityStatusEnabled)
            assertFalse(settings.discordWebhookEnabled)
            assertEquals("", settings.discordWebhookUrl)
            assertFalse(settings.telegramEnabled)
            assertFalse(settings.emailEnabled)
            assertEquals(587, settings.emailSmtpPort)
            assertTrue(settings.emailUseTls)
            assertFalse(settings.showDebugLogs)
            assertEquals(ProxyHealthCheckInterval.OFF, settings.proxyHealthCheckInterval)
        }

    @Test
    fun `run emits every configured preference mapped onto the combined settings`() =
        runTest {
            val appPreferences = InMemoryApplicationPreferenceRepository()
            val notificationSettings = InMemoryNotificationSettingsRepository()
            notificationSettings.setDesktopNotificationsEnabled(false)
            appPreferences.setDevelopmentScriptsEnabled(true)
            appPreferences.setDiscordActivityStatusEnabled(false)
            notificationSettings.setDiscordWebhookEnabled(true)
            notificationSettings.setDiscordWebhookUrl("https://discord.example/hook")
            notificationSettings.setTelegramEnabled(true)
            notificationSettings.setTelegramBotToken("bot-token")
            notificationSettings.setTelegramChatId("chat-id")
            notificationSettings.setEmailEnabled(true)
            notificationSettings.setEmailSmtpHost("smtp.example.com")
            notificationSettings.setEmailSmtpPort(25)
            notificationSettings.setEmailUsername("user")
            notificationSettings.setEmailPassword("secret")
            notificationSettings.setEmailFrom("from@example.com")
            notificationSettings.setEmailTo("to@example.com")
            notificationSettings.setEmailUseTls(false)
            appPreferences.setShowDebugLogs(true)
            appPreferences.setProxyHealthCheckInterval(ProxyHealthCheckInterval.EVERY_6_HOURS)

            val interactor = GetApplicationPreferencesSettingsInteractor(appPreferences, notificationSettings)
            val settings = interactor.run(Interactor.None()).first()

            assertFalse(settings.desktopNotificationsEnabled)
            assertTrue(settings.developmentScriptsEnabled)
            assertFalse(settings.discordActivityStatusEnabled)
            assertTrue(settings.discordWebhookEnabled)
            assertEquals("https://discord.example/hook", settings.discordWebhookUrl)
            assertTrue(settings.telegramEnabled)
            assertEquals("bot-token", settings.telegramBotToken)
            assertEquals("chat-id", settings.telegramChatId)
            assertTrue(settings.emailEnabled)
            assertEquals("smtp.example.com", settings.emailSmtpHost)
            assertEquals(25, settings.emailSmtpPort)
            assertEquals("user", settings.emailUsername)
            assertEquals("secret", settings.emailPassword)
            assertEquals("from@example.com", settings.emailFrom)
            assertEquals("to@example.com", settings.emailTo)
            assertFalse(settings.emailUseTls)
            assertTrue(settings.showDebugLogs)
            assertEquals(ProxyHealthCheckInterval.EVERY_6_HOURS, settings.proxyHealthCheckInterval)
        }
}

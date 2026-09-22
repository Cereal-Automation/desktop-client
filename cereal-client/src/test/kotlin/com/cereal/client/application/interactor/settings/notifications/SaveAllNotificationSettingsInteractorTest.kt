package com.cereal.client.application.interactor.settings.notifications

import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SaveAllNotificationSettingsInteractorTest {
    private lateinit var applicationPreferenceRepository: InMemoryNotificationSettingsRepository
    private lateinit var interactor: SaveAllNotificationSettingsInteractor

    @BeforeEach
    fun setUp() {
        applicationPreferenceRepository = InMemoryNotificationSettingsRepository()
        interactor = SaveAllNotificationSettingsInteractor(applicationPreferenceRepository)
    }

    @Test
    fun `run saves Discord settings when provided`() =
        runTest {
            interactor.run(
                SaveAllNotificationSettingsInteractor.Params(
                    discordEnabled = true,
                    discordWebhookUrl = "https://discord.com/api/webhooks/123/abc",
                ),
            )

            val settings = applicationPreferenceRepository.getApplicationPreferenceSettings()
            assertEquals(true, settings.discordWebhookEnabled)
            assertEquals("https://discord.com/api/webhooks/123/abc", settings.discordWebhookUrl)
        }

    @Test
    fun `run saves Telegram settings when provided`() =
        runTest {
            interactor.run(
                SaveAllNotificationSettingsInteractor.Params(
                    telegramEnabled = true,
                    telegramBotToken = "123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11",
                    telegramChatId = "123456789",
                ),
            )

            val settings = applicationPreferenceRepository.getApplicationPreferenceSettings()
            assertEquals(true, settings.telegramEnabled)
            assertEquals("123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11", settings.telegramBotToken)
            assertEquals("123456789", settings.telegramChatId)
        }

    @Test
    fun `run saves Desktop notification settings when provided`() =
        runTest {
            // Pre-set to false so persisting true is an observable change, not just the default.
            applicationPreferenceRepository.setDesktopNotificationsEnabled(false)

            interactor.run(
                SaveAllNotificationSettingsInteractor.Params(desktopNotificationsEnabled = true),
            )

            assertEquals(true, applicationPreferenceRepository.getApplicationPreferenceSettings().desktopNotificationsEnabled)
        }

    @Test
    fun `run saves all settings when all provided`() =
        runTest {
            interactor.run(
                SaveAllNotificationSettingsInteractor.Params(
                    discordEnabled = true,
                    discordWebhookUrl = "https://discord.com/api/webhooks/123/abc",
                    telegramEnabled = false,
                    telegramBotToken = "token123",
                    telegramChatId = "chat456",
                    desktopNotificationsEnabled = true,
                ),
            )

            val settings = applicationPreferenceRepository.getApplicationPreferenceSettings()
            assertEquals(true, settings.discordWebhookEnabled)
            assertEquals("https://discord.com/api/webhooks/123/abc", settings.discordWebhookUrl)
            assertEquals(false, settings.telegramEnabled)
            assertEquals("token123", settings.telegramBotToken)
            assertEquals("chat456", settings.telegramChatId)
            assertEquals(true, settings.desktopNotificationsEnabled)
        }

    @Test
    fun `run does not save Discord settings when not provided`() =
        runTest {
            // Seed distinctive Discord state; running with only Telegram must leave it untouched.
            applicationPreferenceRepository.setDiscordWebhookEnabled(true)
            applicationPreferenceRepository.setDiscordWebhookUrl("https://preexisting.example/webhook")

            interactor.run(SaveAllNotificationSettingsInteractor.Params(telegramEnabled = true))

            val settings = applicationPreferenceRepository.getApplicationPreferenceSettings()
            assertEquals(true, settings.discordWebhookEnabled)
            assertEquals("https://preexisting.example/webhook", settings.discordWebhookUrl)
        }

    @Test
    fun `run does not save Telegram settings when not provided`() =
        runTest {
            applicationPreferenceRepository.setTelegramEnabled(true)
            applicationPreferenceRepository.setTelegramBotToken("preexisting-token")
            applicationPreferenceRepository.setTelegramChatId("preexisting-chat")

            interactor.run(SaveAllNotificationSettingsInteractor.Params(discordEnabled = true))

            val settings = applicationPreferenceRepository.getApplicationPreferenceSettings()
            assertEquals(true, settings.telegramEnabled)
            assertEquals("preexisting-token", settings.telegramBotToken)
            assertEquals("preexisting-chat", settings.telegramChatId)
        }

    @Test
    fun `run does not save Desktop settings when not provided`() =
        runTest {
            applicationPreferenceRepository.setDesktopNotificationsEnabled(false)

            interactor.run(SaveAllNotificationSettingsInteractor.Params(discordEnabled = true))

            assertEquals(false, applicationPreferenceRepository.getApplicationPreferenceSettings().desktopNotificationsEnabled)
        }

    @Test
    fun `run does nothing when no params provided`() =
        runTest {
            applicationPreferenceRepository.setDesktopNotificationsEnabled(false)
            applicationPreferenceRepository.setDiscordWebhookEnabled(true)
            applicationPreferenceRepository.setDiscordWebhookUrl("https://preexisting.example/webhook")
            applicationPreferenceRepository.setTelegramEnabled(true)

            interactor.run(SaveAllNotificationSettingsInteractor.Params())

            val settings = applicationPreferenceRepository.getApplicationPreferenceSettings()
            assertEquals(false, settings.desktopNotificationsEnabled)
            assertEquals(true, settings.discordWebhookEnabled)
            assertEquals("https://preexisting.example/webhook", settings.discordWebhookUrl)
            assertEquals(true, settings.telegramEnabled)
        }

    @Test
    fun `run disables Discord when set to false`() =
        runTest {
            applicationPreferenceRepository.setDiscordWebhookEnabled(true)

            interactor.run(SaveAllNotificationSettingsInteractor.Params(discordEnabled = false))

            assertEquals(false, applicationPreferenceRepository.getApplicationPreferenceSettings().discordWebhookEnabled)
        }

    @Test
    fun `run disables Telegram when set to false`() =
        runTest {
            applicationPreferenceRepository.setTelegramEnabled(true)

            interactor.run(SaveAllNotificationSettingsInteractor.Params(telegramEnabled = false))

            assertEquals(false, applicationPreferenceRepository.getApplicationPreferenceSettings().telegramEnabled)
        }

    @Test
    fun `run disables Desktop notifications when set to false`() =
        runTest {
            interactor.run(SaveAllNotificationSettingsInteractor.Params(desktopNotificationsEnabled = false))

            assertEquals(false, applicationPreferenceRepository.getApplicationPreferenceSettings().desktopNotificationsEnabled)
        }

    @Test
    fun `run saves empty webhook URL`() =
        runTest {
            applicationPreferenceRepository.setDiscordWebhookUrl("https://preexisting.example/webhook")

            interactor.run(SaveAllNotificationSettingsInteractor.Params(discordWebhookUrl = ""))

            assertEquals("", applicationPreferenceRepository.getApplicationPreferenceSettings().discordWebhookUrl)
        }

    @Test
    fun `run saves empty Telegram bot token`() =
        runTest {
            applicationPreferenceRepository.setTelegramBotToken("preexisting-token")

            interactor.run(SaveAllNotificationSettingsInteractor.Params(telegramBotToken = ""))

            assertEquals("", applicationPreferenceRepository.getApplicationPreferenceSettings().telegramBotToken)
        }

    @Test
    fun `run saves empty Telegram chat ID`() =
        runTest {
            applicationPreferenceRepository.setTelegramChatId("preexisting-chat")

            interactor.run(SaveAllNotificationSettingsInteractor.Params(telegramChatId = ""))

            assertEquals("", applicationPreferenceRepository.getApplicationPreferenceSettings().telegramChatId)
        }
}

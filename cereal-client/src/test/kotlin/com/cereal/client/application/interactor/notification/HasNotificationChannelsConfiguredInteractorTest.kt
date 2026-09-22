package com.cereal.client.application.interactor.notification

import com.cereal.client.domain.model.notification.DiscordOverrides
import com.cereal.client.domain.model.notification.EmailOverrides
import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.domain.model.notification.TelegramOverrides
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HasNotificationChannelsConfiguredInteractorTest {
    private fun emailOverrides(
        smtpHost: String = "smtp.example.com",
        from: String = "from@example.com",
        to: String = "to@example.com",
    ) = EmailOverrides(
        smtpHost = smtpHost,
        smtpPort = 587,
        username = "user",
        password = "pass",
        from = from,
        to = to,
        useTls = true,
    )

    @Test
    fun `run returns false when nothing is configured`() =
        runTest {
            val repository = InMemoryNotificationSettingsRepository()
            val interactor = HasNotificationChannelsConfiguredInteractor(repository)

            assertFalse(interactor.run(HasNotificationChannelsConfiguredInteractor.Params(scriptOverrides = null)))
        }

    @Test
    fun `run returns true when global Discord webhook is enabled`() =
        runTest {
            val repository = InMemoryNotificationSettingsRepository()
            repository.setDiscordWebhookEnabled(true)
            val interactor = HasNotificationChannelsConfiguredInteractor(repository)

            assertTrue(interactor.run(HasNotificationChannelsConfiguredInteractor.Params(scriptOverrides = null)))
        }

    @Test
    fun `run returns true when global Telegram is enabled`() =
        runTest {
            val repository = InMemoryNotificationSettingsRepository()
            repository.setTelegramEnabled(true)
            val interactor = HasNotificationChannelsConfiguredInteractor(repository)

            assertTrue(interactor.run(HasNotificationChannelsConfiguredInteractor.Params(scriptOverrides = null)))
        }

    @Test
    fun `run returns true when global Email is enabled`() =
        runTest {
            val repository = InMemoryNotificationSettingsRepository()
            repository.setEmailEnabled(true)
            val interactor = HasNotificationChannelsConfiguredInteractor(repository)

            assertTrue(interactor.run(HasNotificationChannelsConfiguredInteractor.Params(scriptOverrides = null)))
        }

    @Test
    fun `run returns true when Discord override has a webhook url`() =
        runTest {
            val repository = InMemoryNotificationSettingsRepository()
            val interactor = HasNotificationChannelsConfiguredInteractor(repository)

            val overrides = ScriptNotificationOverrides(discordOverrides = DiscordOverrides(webhookUrl = "https://hook"))

            assertTrue(interactor.run(HasNotificationChannelsConfiguredInteractor.Params(scriptOverrides = overrides)))
        }

    @Test
    fun `run returns false when Discord override webhook url is blank`() =
        runTest {
            val repository = InMemoryNotificationSettingsRepository()
            val interactor = HasNotificationChannelsConfiguredInteractor(repository)

            val overrides = ScriptNotificationOverrides(discordOverrides = DiscordOverrides(webhookUrl = "   "))

            assertFalse(interactor.run(HasNotificationChannelsConfiguredInteractor.Params(scriptOverrides = overrides)))
        }

    @Test
    fun `run returns true when Telegram override has token and chat id`() =
        runTest {
            val repository = InMemoryNotificationSettingsRepository()
            val interactor = HasNotificationChannelsConfiguredInteractor(repository)

            val overrides =
                ScriptNotificationOverrides(
                    telegramOverrides = TelegramOverrides(botToken = "token", chatId = "chat"),
                )

            assertTrue(interactor.run(HasNotificationChannelsConfiguredInteractor.Params(scriptOverrides = overrides)))
        }

    @Test
    fun `run returns false when Telegram override is missing chat id`() =
        runTest {
            val repository = InMemoryNotificationSettingsRepository()
            val interactor = HasNotificationChannelsConfiguredInteractor(repository)

            val overrides =
                ScriptNotificationOverrides(
                    telegramOverrides = TelegramOverrides(botToken = "token", chatId = ""),
                )

            assertFalse(interactor.run(HasNotificationChannelsConfiguredInteractor.Params(scriptOverrides = overrides)))
        }

    @Test
    fun `run returns true when Email override has all required fields`() =
        runTest {
            val repository = InMemoryNotificationSettingsRepository()
            val interactor = HasNotificationChannelsConfiguredInteractor(repository)

            val overrides = ScriptNotificationOverrides(emailOverrides = emailOverrides())

            assertTrue(interactor.run(HasNotificationChannelsConfiguredInteractor.Params(scriptOverrides = overrides)))
        }

    @Test
    fun `run returns false when Email override is missing the smtp host`() =
        runTest {
            val repository = InMemoryNotificationSettingsRepository()
            val interactor = HasNotificationChannelsConfiguredInteractor(repository)

            val overrides = ScriptNotificationOverrides(emailOverrides = emailOverrides(smtpHost = ""))

            assertFalse(interactor.run(HasNotificationChannelsConfiguredInteractor.Params(scriptOverrides = overrides)))
        }

    @Test
    fun `run returns false when Email override is missing the recipient`() =
        runTest {
            val repository = InMemoryNotificationSettingsRepository()
            val interactor = HasNotificationChannelsConfiguredInteractor(repository)

            val overrides = ScriptNotificationOverrides(emailOverrides = emailOverrides(to = ""))

            assertFalse(interactor.run(HasNotificationChannelsConfiguredInteractor.Params(scriptOverrides = overrides)))
        }
}

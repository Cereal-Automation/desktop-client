package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.settings.ProxyHealthCheckInterval
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.preference.ApplicationPreferenceKey
import com.cereal.client.infrastructure.data.repository.fixtures.FakeKeyValueDataSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NotificationSettingsRepositoryImplTest {
    private lateinit var keyValueDataSource: FakeKeyValueDataSource
    private lateinit var userSession: UserSession
    private lateinit var repository: NotificationSettingsRepositoryImpl

    // Shares the key-value store with [repository]; used to seed the app-wide fields that the
    // aggregate read folds in alongside the notification fields.
    private lateinit var appPreferences: ApplicationPreferenceRepositoryImpl

    private val user =
        User(
            id = "user-1",
            name = "Test User",
            email = "test@example.com",
            encryptionKey = "encryption-key",
            accessToken = "access-token",
        )

    @BeforeEach
    fun setUp() {
        keyValueDataSource = FakeKeyValueDataSource()
        userSession = mockk(relaxed = true)
        coEvery { userSession.requireUser() } returns user
        repository = NotificationSettingsRepositoryImpl(keyValueDataSource, userSession)
        appPreferences = ApplicationPreferenceRepositoryImpl(keyValueDataSource, userSession)
    }

    @Test
    fun `desktop notifications round trips`() =
        runTest {
            repository.setDesktopNotificationsEnabled(false)
            assertEquals(false, repository.isDesktopNotificationsEnabled().first())

            repository.setDesktopNotificationsEnabled(true)
            assertEquals(true, repository.isDesktopNotificationsEnabled().first())
        }

    @Test
    fun `desktop notifications returns default when empty`() =
        runTest {
            assertEquals(
                ApplicationPreferenceKey.DesktopNotificationsEnabled.defaultValue,
                repository.isDesktopNotificationsEnabled().first(),
            )
        }

    @Test
    fun `discord webhook enabled round trips`() =
        runTest {
            repository.setDiscordWebhookEnabled(true)
            assertEquals(true, repository.isDiscordWebhookEnabled().first())
        }

    @Test
    fun `discord webhook enabled returns default when empty`() =
        runTest {
            assertEquals(
                ApplicationPreferenceKey.DiscordWebhookEnabled.defaultValue,
                repository.isDiscordWebhookEnabled().first(),
            )
        }

    @Test
    fun `discord webhook url round trips`() =
        runTest {
            repository.setDiscordWebhookUrl("https://discord.test/webhook")
            assertEquals("https://discord.test/webhook", repository.getDiscordWebhookUrl().first())
        }

    @Test
    fun `discord webhook url returns default when empty`() =
        runTest {
            assertEquals(
                ApplicationPreferenceKey.DiscordWebhookUrl.defaultValue,
                repository.getDiscordWebhookUrl().first(),
            )
        }

    @Test
    fun `telegram enabled round trips`() =
        runTest {
            repository.setTelegramEnabled(true)
            assertEquals(true, repository.isTelegramEnabled().first())
        }

    @Test
    fun `telegram enabled returns default when empty`() =
        runTest {
            assertEquals(
                ApplicationPreferenceKey.TelegramEnabled.defaultValue,
                repository.isTelegramEnabled().first(),
            )
        }

    @Test
    fun `telegram bot token round trips`() =
        runTest {
            repository.setTelegramBotToken("bot-token-123")
            assertEquals("bot-token-123", repository.getTelegramBotToken().first())
        }

    @Test
    fun `telegram bot token returns default when empty`() =
        runTest {
            assertEquals(
                ApplicationPreferenceKey.TelegramBotToken.defaultValue,
                repository.getTelegramBotToken().first(),
            )
        }

    @Test
    fun `telegram chat id round trips`() =
        runTest {
            repository.setTelegramChatId("chat-id-456")
            assertEquals("chat-id-456", repository.getTelegramChatId().first())
        }

    @Test
    fun `telegram chat id returns default when empty`() =
        runTest {
            assertEquals(
                ApplicationPreferenceKey.TelegramChatId.defaultValue,
                repository.getTelegramChatId().first(),
            )
        }

    @Test
    fun `email enabled round trips`() =
        runTest {
            repository.setEmailEnabled(true)
            assertEquals(true, repository.isEmailEnabled().first())
        }

    @Test
    fun `email enabled returns default when empty`() =
        runTest {
            assertEquals(
                ApplicationPreferenceKey.EmailEnabled.defaultValue,
                repository.isEmailEnabled().first(),
            )
        }

    @Test
    fun `email smtp host round trips`() =
        runTest {
            repository.setEmailSmtpHost("smtp.test.com")
            assertEquals("smtp.test.com", repository.getEmailSmtpHost().first())
        }

    @Test
    fun `email smtp host returns default when empty`() =
        runTest {
            assertEquals(
                ApplicationPreferenceKey.EmailSmtpHost.defaultValue,
                repository.getEmailSmtpHost().first(),
            )
        }

    @Test
    fun `email smtp port round trips`() =
        runTest {
            repository.setEmailSmtpPort(2525)
            assertEquals(2525, repository.getEmailSmtpPort().first())
        }

    @Test
    fun `email smtp port returns default when empty`() =
        runTest {
            assertEquals(
                ApplicationPreferenceKey.EmailSmtpPort.defaultValue,
                repository.getEmailSmtpPort().first(),
            )
        }

    @Test
    fun `email username round trips`() =
        runTest {
            repository.setEmailUsername("user@test.com")
            assertEquals("user@test.com", repository.getEmailUsername().first())
        }

    @Test
    fun `email username returns default when empty`() =
        runTest {
            assertEquals(
                ApplicationPreferenceKey.EmailUsername.defaultValue,
                repository.getEmailUsername().first(),
            )
        }

    @Test
    fun `email password round trips`() =
        runTest {
            repository.setEmailPassword("secret")
            assertEquals("secret", repository.getEmailPassword().first())
        }

    @Test
    fun `email password returns default when empty`() =
        runTest {
            assertEquals(
                ApplicationPreferenceKey.EmailPassword.defaultValue,
                repository.getEmailPassword().first(),
            )
        }

    @Test
    fun `email from round trips`() =
        runTest {
            repository.setEmailFrom("from@test.com")
            assertEquals("from@test.com", repository.getEmailFrom().first())
        }

    @Test
    fun `email from returns default when empty`() =
        runTest {
            assertEquals(
                ApplicationPreferenceKey.EmailFrom.defaultValue,
                repository.getEmailFrom().first(),
            )
        }

    @Test
    fun `email to round trips`() =
        runTest {
            repository.setEmailTo("to@test.com")
            assertEquals("to@test.com", repository.getEmailTo().first())
        }

    @Test
    fun `email to returns default when empty`() =
        runTest {
            assertEquals(
                ApplicationPreferenceKey.EmailTo.defaultValue,
                repository.getEmailTo().first(),
            )
        }

    @Test
    fun `email use tls round trips`() =
        runTest {
            repository.setEmailUseTls(false)
            assertEquals(false, repository.getEmailUseTls().first())
        }

    @Test
    fun `email use tls returns default when empty`() =
        runTest {
            assertEquals(
                ApplicationPreferenceKey.EmailUseTls.defaultValue,
                repository.getEmailUseTls().first(),
            )
        }

    @Test
    fun `notification center last seen at round trips`() =
        runTest {
            repository.setNotificationCenterLastSeenAt(1234L)
            assertEquals(1234L, repository.getNotificationCenterLastSeenAt().first())
        }

    @Test
    fun `notification center last seen at returns default when empty`() =
        runTest {
            assertEquals(
                ApplicationPreferenceKey.NotificationCenterLastSeenAt.defaultValue,
                repository.getNotificationCenterLastSeenAt().first(),
            )
        }

    @Test
    fun `getApplicationPreferenceSettings aggregates all stored preferences`() =
        runTest {
            repository.setDesktopNotificationsEnabled(false)
            appPreferences.setDevelopmentScriptsEnabled(true)
            appPreferences.setDiscordActivityStatusEnabled(false)
            repository.setDiscordWebhookEnabled(true)
            repository.setDiscordWebhookUrl("https://discord.test/webhook")
            repository.setTelegramEnabled(true)
            repository.setTelegramBotToken("bot-token")
            repository.setTelegramChatId("chat-id")
            repository.setEmailEnabled(true)
            repository.setEmailSmtpHost("smtp.test.com")
            repository.setEmailSmtpPort(2525)
            repository.setEmailUsername("user@test.com")
            repository.setEmailPassword("secret")
            repository.setEmailFrom("from@test.com")
            repository.setEmailTo("to@test.com")
            repository.setEmailUseTls(false)
            appPreferences.setShowDebugLogs(true)
            appPreferences.setProxyHealthCheckInterval(ProxyHealthCheckInterval.EVERY_24_HOURS)

            val settings = repository.getApplicationPreferenceSettings()

            assertEquals(false, settings.desktopNotificationsEnabled)
            assertEquals(true, settings.developmentScriptsEnabled)
            assertEquals(false, settings.discordActivityStatusEnabled)
            assertEquals(true, settings.discordWebhookEnabled)
            assertEquals("https://discord.test/webhook", settings.discordWebhookUrl)
            assertEquals(true, settings.telegramEnabled)
            assertEquals("bot-token", settings.telegramBotToken)
            assertEquals("chat-id", settings.telegramChatId)
            assertEquals(true, settings.emailEnabled)
            assertEquals("smtp.test.com", settings.emailSmtpHost)
            assertEquals(2525, settings.emailSmtpPort)
            assertEquals("user@test.com", settings.emailUsername)
            assertEquals("secret", settings.emailPassword)
            assertEquals("from@test.com", settings.emailFrom)
            assertEquals("to@test.com", settings.emailTo)
            assertEquals(false, settings.emailUseTls)
            assertEquals(true, settings.showDebugLogs)
            assertEquals(ProxyHealthCheckInterval.EVERY_24_HOURS, settings.proxyHealthCheckInterval)
        }

    @Test
    fun `getApplicationPreferenceSettings returns defaults when store is empty`() =
        runTest {
            val settings = repository.getApplicationPreferenceSettings()

            assertEquals(ApplicationPreferenceKey.DesktopNotificationsEnabled.defaultValue, settings.desktopNotificationsEnabled)
            assertEquals(ApplicationPreferenceKey.DevelopmentScriptsEnabled.defaultValue, settings.developmentScriptsEnabled)
            assertEquals(ApplicationPreferenceKey.DiscordActivityStatusEnabled.defaultValue, settings.discordActivityStatusEnabled)
            assertEquals(ApplicationPreferenceKey.DiscordWebhookEnabled.defaultValue, settings.discordWebhookEnabled)
            assertEquals(ApplicationPreferenceKey.DiscordWebhookUrl.defaultValue, settings.discordWebhookUrl)
            assertEquals(ApplicationPreferenceKey.TelegramEnabled.defaultValue, settings.telegramEnabled)
            assertEquals(ApplicationPreferenceKey.TelegramBotToken.defaultValue, settings.telegramBotToken)
            assertEquals(ApplicationPreferenceKey.TelegramChatId.defaultValue, settings.telegramChatId)
            assertEquals(ApplicationPreferenceKey.EmailEnabled.defaultValue, settings.emailEnabled)
            assertEquals(ApplicationPreferenceKey.EmailSmtpHost.defaultValue, settings.emailSmtpHost)
            assertEquals(ApplicationPreferenceKey.EmailSmtpPort.defaultValue, settings.emailSmtpPort)
            assertEquals(ApplicationPreferenceKey.EmailUsername.defaultValue, settings.emailUsername)
            assertEquals(ApplicationPreferenceKey.EmailPassword.defaultValue, settings.emailPassword)
            assertEquals(ApplicationPreferenceKey.EmailFrom.defaultValue, settings.emailFrom)
            assertEquals(ApplicationPreferenceKey.EmailTo.defaultValue, settings.emailTo)
            assertEquals(ApplicationPreferenceKey.EmailUseTls.defaultValue, settings.emailUseTls)
            assertEquals(ApplicationPreferenceKey.ShowDebugLogs.defaultValue, settings.showDebugLogs)
            assertEquals(ProxyHealthCheckInterval.Default, settings.proxyHealthCheckInterval)
        }
}

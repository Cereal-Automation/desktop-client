package com.cereal.client.application.interactor.notification

import com.cereal.client.domain.model.notification.DiscordNotificationData
import com.cereal.client.domain.model.notification.DiscordOverrides
import com.cereal.client.domain.model.notification.EmailNotificationData
import com.cereal.client.domain.model.notification.EmailOverrides
import com.cereal.client.domain.model.notification.NotificationChannelType
import com.cereal.client.domain.model.notification.NotificationDeliveryStatus
import com.cereal.client.domain.model.notification.NotificationHistoryAttempt
import com.cereal.client.domain.model.notification.NotificationResolver
import com.cereal.client.domain.model.notification.ScriptDiscordNotification
import com.cereal.client.domain.model.notification.ScriptNotification
import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.domain.model.notification.SystemNotificationData
import com.cereal.client.domain.model.notification.TelegramNotificationData
import com.cereal.client.domain.model.notification.TelegramOverrides
import com.cereal.client.domain.model.notification.TelegramParseMode
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationHistoryRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationSettingsRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryNotificationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SendNotificationFromScriptInstanceInteractorTest {
    private lateinit var notificationRepository: InMemoryNotificationProvider
    private lateinit var applicationPreferenceRepository: InMemoryNotificationSettingsRepository
    private lateinit var notificationHistoryRepository: InMemoryNotificationHistoryRepository
    private lateinit var interactor: SendNotificationFromScriptInstanceInteractor

    @BeforeEach
    fun setUp() {
        notificationRepository = InMemoryNotificationProvider()
        applicationPreferenceRepository = InMemoryNotificationSettingsRepository()
        notificationHistoryRepository = InMemoryNotificationHistoryRepository()
        interactor =
            SendNotificationFromScriptInstanceInteractor(
                notificationRepository,
                applicationPreferenceRepository,
                notificationHistoryRepository,
                NotificationResolver(),
            )
    }

    /**
     * Disables every channel (the in-memory repo defaults desktop notifications to on) and seeds
     * valid resolution settings, so each test only has to enable the channel it exercises.
     */
    private suspend fun configureDefaults() {
        applicationPreferenceRepository.apply {
            setDesktopNotificationsEnabled(false)
            setDiscordWebhookEnabled(false)
            setTelegramEnabled(false)
            setEmailEnabled(false)
            setDiscordWebhookUrl("https://discord.com/api/webhooks/123/abc")
            setTelegramBotToken("123:token")
            setTelegramChatId("123")
            setEmailSmtpHost("smtp.example.com")
            setEmailSmtpPort(587)
            setEmailUsername("user")
            setEmailPassword("pass")
            setEmailFrom("from@example.com")
            setEmailTo("to@example.com")
            setEmailUseTls(true)
        }
    }

    private suspend fun attemptsFor(taskId: String): List<NotificationHistoryAttempt> {
        val history = notificationHistoryRepository.observeByTaskId(taskId).first().single()
        return notificationHistoryRepository.observeAttempts(history.id).first()
    }

    private fun params(
        notification: ScriptNotification,
        scriptPackageInstance: ScriptPackageInstance? = null,
    ) = SendNotificationFromScriptInstanceInteractor.Params(
        notification = notification,
        taskId = "test-task-id",
        scriptPackageInstance = scriptPackageInstance,
    )

    @Test
    fun `run sends SystemNotification only when enabled`() =
        runTest {
            configureDefaults()
            val notification = ScriptNotification(title = "Title", message = "Message")

            applicationPreferenceRepository.setDesktopNotificationsEnabled(true)
            interactor.run(params(notification))
            assertEquals(1, notificationRepository.sent.size)
            assertTrue(notificationRepository.sent.single() is SystemNotificationData)

            notificationRepository.sent.clear()
            applicationPreferenceRepository.setDesktopNotificationsEnabled(false)
            interactor.run(params(notification))
            assertTrue(notificationRepository.sent.isEmpty())
        }

    @Test
    fun `run sends DiscordNotification when specific notification provided and enabled`() =
        runTest {
            configureDefaults()
            val notification =
                ScriptNotification(
                    title = "Title",
                    message = "Message",
                    discordMessage =
                        ScriptDiscordNotification(
                            content = "Discord Content",
                            webhookUrl = "https://discord.com/api/webhooks/1/a",
                        ),
                )

            applicationPreferenceRepository.setDiscordWebhookEnabled(true)
            interactor.run(params(notification))

            val sent = notificationRepository.sent.single() as DiscordNotificationData
            assertEquals("Discord Content", sent.content)
        }

    @Test
    fun `run falls back to converting SystemNotification to Discord when enabled`() =
        runTest {
            configureDefaults()
            val notification = ScriptNotification(title = "Title", message = "Message")

            applicationPreferenceRepository.setDiscordWebhookEnabled(true)
            interactor.run(params(notification))

            val sent = notificationRepository.sent.single() as DiscordNotificationData
            assertEquals("**Title**\nMessage", sent.content)
        }

    @Test
    fun `run sends TelegramNotification logic (specific vs fallback)`() =
        runTest {
            configureDefaults()
            val notification = ScriptNotification(title = "Title", message = "Message")

            applicationPreferenceRepository.setTelegramEnabled(true)
            interactor.run(params(notification))

            val sent = notificationRepository.sent.single() as TelegramNotificationData
            assertEquals("*Title*\nMessage", sent.text)
            assertEquals(TelegramParseMode.MARKDOWN, sent.parseMode)
        }

    @Test
    fun `run sends EmailNotification logic (specific vs fallback)`() =
        runTest {
            configureDefaults()
            val notification = ScriptNotification(title = "Title", message = "Message")

            applicationPreferenceRepository.setEmailEnabled(true)
            interactor.run(params(notification))

            val sent = notificationRepository.sent.single() as EmailNotificationData
            assertEquals("Title", sent.subject)
            assertEquals("Message", sent.body)
        }

    @Test
    fun `run records a FAILURE attempt (and does not throw) when required settings missing during resolution`() =
        runTest {
            configureDefaults()
            val notification = ScriptNotification(title = "Title", message = "Message")

            applicationPreferenceRepository.setDiscordWebhookEnabled(true)
            applicationPreferenceRepository.setDiscordWebhookUrl("") // missing -> resolution throws internally

            interactor.run(params(notification))

            assertTrue(notificationRepository.sent.isEmpty())
            assertTrue(
                attemptsFor("test-task-id").any {
                    it.channel == NotificationChannelType.DISCORD &&
                        it.status == NotificationDeliveryStatus.FAILURE
                },
            )
        }

    @Test
    fun `run continues to later channels when an earlier channel fails resolution`() =
        runTest {
            configureDefaults()
            val notification = ScriptNotification(title = "Title", message = "Message")

            applicationPreferenceRepository.setDiscordWebhookEnabled(true)
            applicationPreferenceRepository.setDiscordWebhookUrl("")
            applicationPreferenceRepository.setDesktopNotificationsEnabled(true)

            interactor.run(params(notification))

            assertTrue(notificationRepository.sent.any { it is SystemNotificationData })

            val attempts = attemptsFor("test-task-id")
            assertTrue(
                attempts.any {
                    it.channel == NotificationChannelType.DISCORD &&
                        it.status == NotificationDeliveryStatus.FAILURE
                },
            )
            assertTrue(
                attempts.any {
                    it.channel == NotificationChannelType.SYSTEM &&
                        it.status == NotificationDeliveryStatus.SUCCESS
                },
            )
        }

    @Test
    fun `run sends DiscordNotification when globally disabled but override present`() =
        runTest {
            configureDefaults()
            val notification = ScriptNotification(title = "Title", message = "Message")
            val overrides =
                ScriptNotificationOverrides(
                    discordOverrides = DiscordOverrides(webhookUrl = "https://discord.com/api/webhooks/override"),
                )
            val scriptInstance = mockk<ScriptPackageInstance>()
            every { scriptInstance.notificationOverrides } returns overrides

            applicationPreferenceRepository.setDiscordWebhookEnabled(false)
            interactor.run(params(notification, scriptInstance))

            val sent = notificationRepository.sent.single() as DiscordNotificationData
            assertEquals("https://discord.com/api/webhooks/override", sent.webhookUrl)
        }

    @Test
    fun `run sends TelegramNotification when globally disabled but override present`() =
        runTest {
            configureDefaults()
            val notification = ScriptNotification(title = "Title", message = "Message")
            val overrides =
                ScriptNotificationOverrides(
                    telegramOverrides = TelegramOverrides(botToken = "123456:override_token", chatId = "override_chat"),
                )
            val scriptInstance = mockk<ScriptPackageInstance>()
            every { scriptInstance.notificationOverrides } returns overrides

            applicationPreferenceRepository.setTelegramEnabled(false)
            interactor.run(params(notification, scriptInstance))

            val sent = notificationRepository.sent.single() as TelegramNotificationData
            assertEquals("123456:override_token", sent.botToken)
            assertEquals("override_chat", sent.chatId)
        }

    @Test
    fun `run sends EmailNotification when globally disabled but override present`() =
        runTest {
            configureDefaults()
            val notification = ScriptNotification(title = "Title", message = "Message")
            val overrides =
                ScriptNotificationOverrides(
                    emailOverrides =
                        EmailOverrides(
                            smtpHost = "smtp.override.com",
                            smtpPort = 25,
                            username = "override_user",
                            password = "override_pass",
                            from = "override@example.com",
                            to = "override_to@example.com",
                            useTls = false,
                        ),
                )
            val scriptInstance = mockk<ScriptPackageInstance>()
            every { scriptInstance.notificationOverrides } returns overrides

            applicationPreferenceRepository.setEmailEnabled(false)
            interactor.run(params(notification, scriptInstance))

            val sent = notificationRepository.sent.single() as EmailNotificationData
            assertEquals("smtp.override.com", sent.smtpHost)
            assertEquals("override_user", sent.username)
        }
}

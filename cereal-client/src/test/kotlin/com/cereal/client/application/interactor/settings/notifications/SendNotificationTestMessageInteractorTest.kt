package com.cereal.client.application.interactor.settings.notifications

import com.cereal.client.domain.model.notification.DiscordNotificationData
import com.cereal.client.domain.model.notification.EmailNotificationData
import com.cereal.client.domain.model.notification.SystemNotificationData
import com.cereal.client.domain.model.notification.TelegramNotificationData
import com.cereal.client.domain.model.notification.TelegramParseMode
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationConfig
import com.cereal.client.infrastructure.provider.inmemory.InMemoryNotificationProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SendNotificationTestMessageInteractorTest {
    private lateinit var notificationRepository: InMemoryNotificationProvider
    private lateinit var interactor: SendNotificationTestMessageInteractor

    @BeforeEach
    fun setUp() {
        notificationRepository = InMemoryNotificationProvider()
        interactor =
            SendNotificationTestMessageInteractor(
                applicationConfig = InMemoryApplicationConfig(),
                notificationRepository = notificationRepository,
            )
    }

    @Test
    fun `run sends a system notification`() =
        runTest {
            interactor.run(SendNotificationTestMessageInteractor.Params.System)

            val sent = assertInstanceOf(SystemNotificationData::class.java, notificationRepository.sent.single())
            assertEquals("Desktop Notification Test", sent.title)
        }

    @Test
    fun `run sends a Discord notification using the configured app name`() =
        runTest {
            val webhookUrl = "https://discord.com/api/webhooks/123/abc"
            interactor.run(SendNotificationTestMessageInteractor.Params.Discord(webhookUrl = webhookUrl))

            val sent = assertInstanceOf(DiscordNotificationData::class.java, notificationRepository.sent.single())
            assertEquals("Cereal", sent.username)
            assertEquals(webhookUrl, sent.webhookUrl)
            assertEquals("Success!", sent.embeds.single().title)
        }

    @Test
    fun `run sends a Telegram notification with markdown parse mode`() =
        runTest {
            interactor.run(
                SendNotificationTestMessageInteractor.Params.Telegram(
                    botToken = "123456:ABC-def_ghi",
                    chatId = "chat-id",
                ),
            )

            val sent = assertInstanceOf(TelegramNotificationData::class.java, notificationRepository.sent.single())
            assertEquals("123456:ABC-def_ghi", sent.botToken)
            assertEquals("chat-id", sent.chatId)
            assertEquals(TelegramParseMode.MARKDOWN, sent.parseMode)
        }

    @Test
    fun `run sends an email notification carrying the smtp settings`() =
        runTest {
            interactor.run(
                SendNotificationTestMessageInteractor.Params.Email(
                    smtpHost = "smtp.example.com",
                    smtpPort = 587,
                    username = "user",
                    password = "secret",
                    from = "from@example.com",
                    to = "to@example.com",
                    useTls = true,
                ),
            )

            val sent = assertInstanceOf(EmailNotificationData::class.java, notificationRepository.sent.single())
            assertEquals("smtp.example.com", sent.smtpHost)
            assertEquals(587, sent.smtpPort)
            assertEquals("to@example.com", sent.to)
            assertEquals(true, sent.useTls)
        }
}

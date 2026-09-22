package com.cereal.client.application.interactor.settings.notifications

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.notification.DiscordNotificationData
import com.cereal.client.domain.model.notification.EmailNotificationData
import com.cereal.client.domain.model.notification.SystemNotificationData
import com.cereal.client.domain.model.notification.TelegramNotificationData
import com.cereal.client.domain.model.notification.TelegramParseMode
import com.cereal.client.infrastructure.provider.inmemory.InMemoryNotificationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Covers every [SendNotificationTestMessageInteractor.Params] subtype (System / Discord / Telegram / Email).
 *
 * The notification repository is the real recording in-memory fake so assertions are on the observable
 * notification it was asked to send. [ApplicationConfig] is a relaxed MockK (a wide interface with no
 * observable state) stubbed only for the one field the interactor reads: [ApplicationConfig.name].
 */
class SendNotificationTestMessageInteractorAdditionalTest {
    private lateinit var applicationConfig: ApplicationConfig
    private lateinit var notificationRepository: InMemoryNotificationProvider
    private lateinit var interactor: SendNotificationTestMessageInteractor

    @BeforeEach
    fun setUp() {
        applicationConfig = mockk(relaxed = true)
        every { applicationConfig.name } returns "Cereal"
        notificationRepository = InMemoryNotificationProvider()
        interactor = SendNotificationTestMessageInteractor(applicationConfig, notificationRepository)
    }

    @Test
    fun `run sends a system notification for the System params`() =
        runTest {
            interactor.run(SendNotificationTestMessageInteractor.Params.System)

            val notification = notificationRepository.sent.single()
            assertInstanceOf(SystemNotificationData::class.java, notification)
            notification as SystemNotificationData
            assertEquals("Desktop Notification Test", notification.title)
        }

    @Test
    fun `run sends a Discord notification carrying the webhook url and app name`() =
        runTest {
            val webhookUrl = "https://discord.com/api/webhooks/123/abc"

            interactor.run(SendNotificationTestMessageInteractor.Params.Discord(webhookUrl = webhookUrl))

            val notification = notificationRepository.sent.single()
            assertInstanceOf(DiscordNotificationData::class.java, notification)
            notification as DiscordNotificationData
            assertEquals(webhookUrl, notification.webhookUrl)
            assertEquals("Cereal", notification.username)
            assertEquals("Success!", notification.embeds.single().title)
        }

    @Test
    fun `run sends a Telegram notification with the bot token and chat id`() =
        runTest {
            interactor.run(
                SendNotificationTestMessageInteractor.Params.Telegram(
                    botToken = "123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11",
                    chatId = "987654321",
                ),
            )

            val notification = notificationRepository.sent.single()
            assertInstanceOf(TelegramNotificationData::class.java, notification)
            notification as TelegramNotificationData
            assertEquals("123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11", notification.botToken)
            assertEquals("987654321", notification.chatId)
            assertEquals(TelegramParseMode.MARKDOWN, notification.parseMode)
        }

    @Test
    fun `run sends an Email notification mapping all params fields`() =
        runTest {
            interactor.run(
                SendNotificationTestMessageInteractor.Params.Email(
                    smtpHost = "smtp.example.com",
                    smtpPort = 587,
                    username = "user@example.com",
                    password = "s3cret",
                    from = "from@example.com",
                    to = "to@example.com",
                    useTls = true,
                ),
            )

            val notification = notificationRepository.sent.single()
            assertInstanceOf(EmailNotificationData::class.java, notification)
            notification as EmailNotificationData
            assertEquals("smtp.example.com", notification.smtpHost)
            assertEquals(587, notification.smtpPort)
            assertEquals("user@example.com", notification.username)
            assertEquals("s3cret", notification.password)
            assertEquals("from@example.com", notification.from)
            assertEquals("to@example.com", notification.to)
            assertEquals(true, notification.useTls)
            assertEquals("Cereal Email Test", notification.subject)
        }
}

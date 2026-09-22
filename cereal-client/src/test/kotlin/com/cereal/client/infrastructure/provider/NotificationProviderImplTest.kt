package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.notification.DiscordNotificationData
import com.cereal.client.domain.model.notification.EmailNotificationData
import com.cereal.client.domain.model.notification.SystemNotificationData
import com.cereal.client.domain.model.notification.TelegramNotificationData
import com.cereal.client.infrastructure.data.notification.DiscordNotificationStrategy
import com.cereal.client.infrastructure.data.notification.EmailNotificationStrategy
import com.cereal.client.infrastructure.data.notification.SystemNotificationStrategy
import com.cereal.client.infrastructure.data.notification.TelegramNotificationStrategy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class NotificationProviderImplTest {
    private lateinit var systemStrategy: SystemNotificationStrategy
    private lateinit var discordStrategy: DiscordNotificationStrategy
    private lateinit var telegramStrategy: TelegramNotificationStrategy
    private lateinit var emailStrategy: EmailNotificationStrategy
    private lateinit var notificationRepository: NotificationProviderImpl

    @BeforeEach
    fun setUp() {
        systemStrategy = mockk(relaxed = true)
        discordStrategy = mockk(relaxed = true)
        telegramStrategy = mockk(relaxed = true)
        emailStrategy = mockk(relaxed = true)

        notificationRepository =
            NotificationProviderImpl(
                systemStrategy,
                discordStrategy,
                telegramStrategy,
                emailStrategy,
            )
    }

    @Test
    fun `sendNotification should call system strategy for SystemNotificationData`() =
        runTest {
            // Arrange
            val notification = SystemNotificationData(title = "Title", message = "Message")

            // Act
            notificationRepository.sendNotification(notification)

            // Assert
            coVerify(exactly = 1) { systemStrategy.send(notification) }
            coVerify(exactly = 0) { discordStrategy.send(any()) }
            coVerify(exactly = 0) { telegramStrategy.send(any()) }
            coVerify(exactly = 0) { emailStrategy.send(any()) }
        }

    @Test
    fun `sendNotification should call discord strategy for DiscordNotificationData`() =
        runTest {
            // Arrange
            val notification =
                DiscordNotificationData(
                    username = "User",
                    content = "Content",
                    webhookUrl = "https://discord.com/api/webhooks/123/abc",
                )

            // Act
            notificationRepository.sendNotification(notification)

            // Assert
            coVerify(exactly = 0) { systemStrategy.send(any()) }
            coVerify(exactly = 1) { discordStrategy.send(notification) }
            coVerify(exactly = 0) { telegramStrategy.send(any()) }
            coVerify(exactly = 0) { emailStrategy.send(any()) }
        }

    @Test
    fun `sendNotification should call telegram strategy for TelegramNotificationData`() =
        runTest {
            // Arrange
            val notification =
                TelegramNotificationData(
                    chatId = "123",
                    text = "Text",
                    botToken = "123:abc",
                )

            // Act
            notificationRepository.sendNotification(notification)

            // Assert
            coVerify(exactly = 0) { systemStrategy.send(any()) }
            coVerify(exactly = 0) { discordStrategy.send(any()) }
            coVerify(exactly = 1) { telegramStrategy.send(notification) }
            coVerify(exactly = 0) { emailStrategy.send(any()) }
        }

    @Test
    fun `sendNotification should call email strategy for EmailNotificationData`() =
        runTest {
            // Arrange
            val notification =
                EmailNotificationData(
                    to = "test@example.com",
                    subject = "Subject",
                    body = "Body",
                    from = "sender@example.com",
                    smtpHost = "smtp.example.com",
                    smtpPort = 587,
                )

            // Act
            notificationRepository.sendNotification(notification)

            // Assert
            coVerify(exactly = 0) { systemStrategy.send(any()) }
            coVerify(exactly = 0) { discordStrategy.send(any()) }
            coVerify(exactly = 0) { telegramStrategy.send(any()) }
            coVerify(exactly = 1) { emailStrategy.send(notification) }
        }

    @Test
    fun `sendNotification should not crash if strategy throws exception`() =
        runTest {
            // Arrange
            val notification = SystemNotificationData(title = "Title", message = "Message")
            coEvery { systemStrategy.send(any()) } throws RuntimeException("Strategy failed")

            // Act & Assert
            assertDoesNotThrow {
                notificationRepository.sendNotification(notification)
            }
        }
}

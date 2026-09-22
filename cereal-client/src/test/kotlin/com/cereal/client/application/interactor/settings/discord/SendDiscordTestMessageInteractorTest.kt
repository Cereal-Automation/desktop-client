package com.cereal.client.application.interactor.settings.discord

import com.cereal.client.application.interactor.settings.notifications.SendNotificationTestMessageInteractor
import com.cereal.client.domain.model.notification.DiscordNotificationData
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationConfig
import com.cereal.client.infrastructure.provider.inmemory.InMemoryNotificationProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for SendNotificationTestMessageInteractor (Discord variant).
 *
 * These tests verify that the interactor properly creates and sends Discord test messages
 * with the correct username, embeds, and other Discord-specific properties. The notification
 * repository is the in-memory recording implementation, so assertions read the captured
 * notification straight from [InMemoryNotificationProvider.sent].
 */
class SendDiscordTestMessageInteractorTest {
    private lateinit var notificationRepository: InMemoryNotificationProvider
    private lateinit var interactor: SendNotificationTestMessageInteractor

    @BeforeEach
    fun setUp() {
        notificationRepository = InMemoryNotificationProvider()
        interactor = SendNotificationTestMessageInteractor(InMemoryApplicationConfig(), notificationRepository)
    }

    @Test
    fun `run should send Discord test message with correct parameters`() =
        runTest {
            val params = SendNotificationTestMessageInteractor.Params.Discord("https://discord.com/api/webhooks/123/abc")

            interactor.run(params)

            val capturedNotification = notificationRepository.sent.single() as DiscordNotificationData

            assertEquals("Cereal", capturedNotification.username)
            assertNotNull(capturedNotification.embeds)
            assertTrue(capturedNotification.embeds.isNotEmpty())

            val embed = capturedNotification.embeds.first()
            assertEquals("6613812", embed.color)
            assertEquals("Success!", embed.title)
            assertTrue(embed.description!!.contains("This is a test from Cereal using your webhook!"))
            assertTrue(embed.description.contains("By seeing this message, it means your webhook is setup correctly."))
        }

    @Test
    fun `run should use application config name for Discord username`() =
        runTest {
            val customAppName = "Custom Cereal App"
            val customInteractor =
                SendNotificationTestMessageInteractor(
                    InMemoryApplicationConfig(name = customAppName),
                    notificationRepository,
                )
            val params =
                SendNotificationTestMessageInteractor.Params.Discord("https://discord.com/api/webhooks/123/abc")

            customInteractor.run(params)

            val capturedNotification = notificationRepository.sent.single() as DiscordNotificationData
            assertEquals(customAppName, capturedNotification.username)
        }

    @Test
    fun `run should throw exception for empty webhook URL`() =
        runTest {
            val params = SendNotificationTestMessageInteractor.Params.Discord("")

            assertThrows<IllegalArgumentException> {
                interactor.run(params)
            }
        }

    @Test
    fun `run should create notification with correct Discord message structure`() =
        runTest {
            val params = SendNotificationTestMessageInteractor.Params.Discord("https://discord.com/api/webhooks/123/abc")

            interactor.run(params)

            val capturedNotification = notificationRepository.sent.single() as DiscordNotificationData

            assertNotNull(capturedNotification.embeds)
            assertTrue(capturedNotification.embeds.isNotEmpty())

            val embed = capturedNotification.embeds.first()
            assertNotNull(embed.title)
            assertNotNull(embed.description)
            assertNotNull(embed.color)

            assertEquals("Success!", embed.title)
            assertTrue(embed.description.contains("test from Cereal"))
            assertTrue(embed.description.contains("webhook is setup correctly"))
            assertEquals("6613812", embed.color)
        }

    @Test
    fun `run should call notification repository exactly once`() =
        runTest {
            val params = SendNotificationTestMessageInteractor.Params.Discord("https://discord.com/api/webhooks/123/abc")

            interactor.run(params)

            assertEquals(1, notificationRepository.sent.size)
        }
}

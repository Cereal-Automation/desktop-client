package com.cereal.client.presentation.headless

import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.interactor.notification.GlobalNotificationConfigReader
import com.cereal.client.domain.model.notification.DiscordNotificationData
import com.cereal.client.domain.model.notification.EmailNotificationData
import com.cereal.client.domain.provider.AppUpdateProvider
import com.cereal.client.domain.provider.NotificationProvider
import com.cereal.client.domain.repository.NotificationSettingsRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAppUpdateProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryNotificationProvider
import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Keys
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import testutil.HeadlessTestScope
import testutil.runHeadlessTest

class HeadlessSettingsTest {
    private val HeadlessTestScope.settings get() = get<NotificationSettingsRepository>()
    private val HeadlessTestScope.provider get() = get<NotificationProvider>() as InMemoryNotificationProvider

    /** Opens tab 4 and moves the cursor to the row labelled [label]. */
    private suspend fun HeadlessTestScope.focus(label: String) {
        press(CharKey('4'))
        awaitText("[4 Settings]")
        awaitText("› ")
        repeat(ROWS) {
            if (screen().any { it.startsWith("›") && label in it }) return
            press(Keys.Down)
            delay(20)
        }
        throw AssertionError("Row $label never focused:\n" + screen().joinToString("\n"))
    }

    private suspend fun HeadlessTestScope.awaitFlag(check: suspend () -> Boolean) {
        repeat(100) {
            if (check()) return
            delay(20)
        }
        throw AssertionError("Setting never saved")
    }

    @Test
    fun `each field saves on commit and secrets are masked`() =
        runHeadlessTest {
            awaitText("1-5 tabs")
            focus("Discord  ")

            press(Keys.Space)
            awaitText("✓ Saved Discord.")
            awaitFlag { settings.isDiscordWebhookEnabled().first() }
            awaitText("Webhook URL")

            press(Keys.Down)
            awaitScreen { lines -> lines.any { it.startsWith("›") && "Webhook URL" in it && "not set" in it } }
            press(Keys.Enter)
            type("https://discord.com/api/webhooks/1/topsecret")
            awaitText("> ********")
            press(Keys.Enter)

            awaitText("✓ Saved Webhook URL.")
            awaitText(FieldForm.SECRET_SET)
            awaitFlag { settings.getDiscordWebhookUrl().first() == "https://discord.com/api/webhooks/1/topsecret" }
            assertTrue(screen().none { "topsecret" in it }, screen().joinToString("\n"))

            // A plain field and the port's validation.
            focus("Email")
            press(Keys.Space)
            awaitText("SMTP port")
            focus("SMTP port")
            press(Keys.Enter)
            press(*List(3) { Keys.Backspace }.toTypedArray())
            type("0")
            press(Keys.Enter)
            awaitText("! Enter a port from 1 to 65535.")
            press(Keys.Backspace)
            type("2525")
            press(Keys.Enter)
            awaitFlag { settings.getEmailSmtpPort().first() == 2525 }
        }

    @Test
    fun `T sends a test on the focused channel and shows the failure`() =
        runHeadlessTest(
            seed = {
                get<NotificationSettingsRepository>().setDiscordWebhookEnabled(true)
                get<NotificationSettingsRepository>().setDiscordWebhookUrl("https://discord.com/api/webhooks/1/abc")
                get<NotificationSettingsRepository>().setEmailEnabled(true)
                get<NotificationSettingsRepository>().apply {
                    setEmailSmtpHost("smtp.example.com")
                    setEmailFrom("from@example.com")
                    setEmailTo("to@example.com")
                }
            },
        ) {
            awaitText("1-5 tabs")
            focus("Webhook URL")
            awaitText("T test")

            press(CharKey('T'))
            awaitText("✓ Test message sent on Discord.")
            assertEquals("https://discord.com/api/webhooks/1/abc", (provider.sent.single() as DiscordNotificationData).webhookUrl)

            provider.failure = { CerealException("Discord rejected the webhook message (HTTP 404).") }
            press(CharKey('T'))
            awaitText("! Discord test failed: Discord rejected the webhook message (HTTP 404).")

            provider.failure = { null }
            focus("SMTP host")
            press(CharKey('T'))
            awaitText("✓ Test message sent on Email.")
            assertEquals("smtp.example.com", (provider.sent.last() as EmailNotificationData).smtpHost)
        }

    @Test
    fun `the desktop toggle is hidden and the system channel is off headless while the stored flag stays on`() =
        runHeadlessTest {
            awaitText("1-5 tabs")
            press(CharKey('4'))
            val screen = awaitText("Discord status")

            assertTrue(screen.none { "Desktop" in it }, screen.joinToString("\n"))
            assertTrue(screen.any { "Discord status" in it && "unavailable" in it })
            assertTrue(settings.isDesktopNotificationsEnabled().first())
            assertFalse(get<GlobalNotificationConfigReader>().read().desktopEnabled)
        }

    @Test
    fun `the no-channel banner shows exactly while all three channels are disabled`() =
        runHeadlessTest {
            awaitScreen { it[1] == NO_CHANNEL }

            settings.setTelegramEnabled(true)
            awaitScreen { it[1].isEmpty() }

            settings.setTelegramEnabled(false)
            awaitScreen { it[1] == NO_CHANNEL }
        }

    @Test
    fun `an available update takes the banner line over the no-channel banner`() =
        runHeadlessTest(
            seed = {
                (get<AppUpdateProvider>() as InMemoryAppUpdateProvider).latestVersion = InMemoryAppUpdateProvider.version("1.2.0", "1.0.0")
            },
        ) {
            awaitText("1-5 tabs")
            press(CharKey('4'))
            awaitText("Discord status")
            assertEquals("Update 1.2.0 available. Press U for the upgrade commands.", screen()[1])
        }

    private companion object {
        const val NO_CHANNEL = "No notification channel set up. Add Discord, Telegram or email on tab 4."
        const val ROWS = 30
    }
}

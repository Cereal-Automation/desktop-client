package com.cereal.client.presentation.tasks.dialog

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.cereal.client.domain.model.notification.DiscordOverrides
import com.cereal.client.domain.model.notification.EmailOverrides
import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.domain.model.notification.TelegramOverrides
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NotificationOverridesSummaryTest {
    @Test
    fun rendersConfiguredChannelsAndMasksSecrets() =
        runScreenTest {
            val overrides =
                ScriptNotificationOverrides(
                    discordOverrides = DiscordOverrides(webhookUrl = "https://discord.example/webhook"),
                    telegramOverrides = TelegramOverrides(botToken = "secret-token", chatId = "12345"),
                    emailOverrides =
                        EmailOverrides(
                            smtpHost = "smtp.example.com",
                            smtpPort = 587,
                            username = "user@example.com",
                            password = "secret-password",
                            from = "from@example.com",
                            to = "to@example.com",
                            useTls = true,
                        ),
                )

            setScreenContent { NotificationOverridesSummary(overrides) }

            onNodeWithText("Notification Overrides").assertIsDisplayed()
            // Non-secret values are shown verbatim.
            onNodeWithText("https://discord.example/webhook").assertIsDisplayed()
            onNodeWithText("12345").assertIsDisplayed()
            onNodeWithText("smtp.example.com").assertIsDisplayed()
            onNodeWithText("587").assertIsDisplayed()
            onNodeWithText("Enabled").assertIsDisplayed()
            // Secrets are masked, never shown in plain text.
            onNodeWithText("secret-token").assertDoesNotExist()
            onNodeWithText("secret-password").assertDoesNotExist()
        }

    @Test
    fun rendersOnlyConfiguredChannels() =
        runScreenTest {
            val overrides =
                ScriptNotificationOverrides(
                    discordOverrides = DiscordOverrides(webhookUrl = "https://discord.example/webhook"),
                )

            setScreenContent { NotificationOverridesSummary(overrides) }

            onNodeWithText("Override Discord Settings").assertIsDisplayed()
            onNodeWithText("Override Telegram Settings").assertDoesNotExist()
            onNodeWithText("Override Email Settings").assertDoesNotExist()
        }
}

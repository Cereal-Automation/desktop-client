package com.cereal.client.presentation.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.cereal.client.presentation.settings.components.DiscordWebhookUrlField
import com.cereal.client.presentation.settings.components.EmailFields
import com.cereal.client.presentation.settings.components.EmailUseTlsField
import com.cereal.client.presentation.settings.components.TelegramFields
import com.cereal.client.presentation.settings.state.DiscordWebhookUrlState
import com.cereal.client.presentation.settings.state.EmailFromState
import com.cereal.client.presentation.settings.state.EmailPasswordState
import com.cereal.client.presentation.settings.state.EmailSmtpHostState
import com.cereal.client.presentation.settings.state.EmailSmtpPortState
import com.cereal.client.presentation.settings.state.EmailToState
import com.cereal.client.presentation.settings.state.EmailUsernameState
import com.cereal.client.presentation.settings.state.TelegramBotTokenState
import com.cereal.client.presentation.settings.state.TelegramChatIdState
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Component-level render/interaction tests for the notification-channel field composables in
 * [com.cereal.client.presentation.settings.components.NotificationChannelFields]. These take direct
 * state params and are not exercised by ApplicationSettingsScreenTest, so they are covered here with
 * minimally-constructed valid args.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class NotificationChannelFieldsTest {
    @Test
    fun discordWebhookFieldRendersLabel() =
        runScreenTest {
            setScreenContent { DiscordWebhookUrlField(DiscordWebhookUrlState()) }

            onNodeWithText("Discord webhook").assertIsDisplayed()
        }

    @Test
    fun telegramFieldsRenderBothLabels() =
        runScreenTest {
            setScreenContent {
                TelegramFields(
                    botTokenState = TelegramBotTokenState(),
                    chatIdState = TelegramChatIdState(),
                )
            }

            onNodeWithText("Telegram bot token").assertIsDisplayed()
            onNodeWithText("Telegram chat ID").assertIsDisplayed()
        }

    @Test
    fun emailFieldsRenderAllLabels() =
        runScreenTest {
            setScreenContent {
                EmailFields(
                    smtpHostState = EmailSmtpHostState(),
                    smtpPortState = EmailSmtpPortState(),
                    usernameState = EmailUsernameState(),
                    passwordState = EmailPasswordState(),
                    fromState = EmailFromState(),
                    toState = EmailToState(),
                    useTls = mutableStateOf(false),
                    onUseTlsChange = {},
                )
            }

            // Use labels that do not collide with field placeholder text.
            onNodeWithText("SMTP Port").assertIsDisplayed()
            onNodeWithText("From Address").assertIsDisplayed()
            onNodeWithText("To Address").assertIsDisplayed()
            onNodeWithText("Use TLS").assertIsDisplayed()
        }

    @Test
    fun emailUseTlsFieldRendersLabelAndDescription() =
        runScreenTest {
            val useTls = mutableStateOf(true)

            setScreenContent {
                EmailUseTlsField(
                    useTls = useTls,
                    onUseTlsChange = {},
                )
            }

            onNodeWithText("Use TLS").assertIsDisplayed()
            onNodeWithText("Enable TLS encryption for secure connection.").assertIsDisplayed()
            assertTrue(useTls.value)
        }
}

package com.cereal.client.presentation.tasks.script.overview.configuration.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.domain.model.notification.DiscordOverrides
import com.cereal.client.domain.model.notification.EmailOverrides
import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.domain.model.notification.TelegramOverrides
import com.cereal.client.presentation.settings.state.DiscordWebhookUrlState
import com.cereal.client.presentation.settings.state.EmailFromState
import com.cereal.client.presentation.settings.state.EmailPasswordState
import com.cereal.client.presentation.settings.state.EmailSmtpHostState
import com.cereal.client.presentation.settings.state.EmailSmtpPortState
import com.cereal.client.presentation.settings.state.EmailToState
import com.cereal.client.presentation.settings.state.EmailUsernameState
import com.cereal.client.presentation.settings.state.TelegramBotTokenState
import com.cereal.client.presentation.settings.state.TelegramChatIdState

/**
 * Form state for per-script notification channel overrides.
 */
class NotificationOverridesForm {
    // Discord override settings
    val discordOverrideEnabled: MutableState<Boolean> = mutableStateOf(false)
    val discordWebhookUrl = DiscordWebhookUrlState()

    // Telegram override settings
    val telegramOverrideEnabled: MutableState<Boolean> = mutableStateOf(false)
    val telegramBotToken = TelegramBotTokenState()
    val telegramChatId = TelegramChatIdState()

    // Email override settings
    val emailOverrideEnabled: MutableState<Boolean> = mutableStateOf(false)
    val emailSmtpHost = EmailSmtpHostState()
    val emailSmtpPort = EmailSmtpPortState()
    val emailUsername = EmailUsernameState()
    val emailPassword = EmailPasswordState()
    val emailFrom = EmailFromState()
    val emailTo = EmailToState()
    val emailUseTls: MutableState<Boolean> = mutableStateOf(true)

    /**
     * Returns true if any override is enabled.
     */
    fun hasAnyOverridesEnabled(): Boolean = discordOverrideEnabled.value || telegramOverrideEnabled.value || emailOverrideEnabled.value

    /**
     * Validates all enabled override fields.
     * @return true if all enabled fields are valid.
     */
    fun validate(): Boolean {
        var isValid = true

        if (discordOverrideEnabled.value) {
            if (!discordWebhookUrl.validate()) isValid = false
        }

        if (telegramOverrideEnabled.value) {
            if (!telegramBotToken.validate()) isValid = false
            if (!telegramChatId.validate()) isValid = false
        }

        if (emailOverrideEnabled.value) {
            if (!emailSmtpHost.validate()) isValid = false
            if (!emailSmtpPort.validate()) isValid = false
            if (!emailUsername.validate()) isValid = false
            if (!emailPassword.validate()) isValid = false
            if (!emailFrom.validate()) isValid = false
            if (!emailTo.validate()) isValid = false
        }

        return isValid
    }

    /**
     * Builds the domain model from the form state.
     * @return ScriptNotificationOverrides or null if no overrides are configured.
     */
    fun toOverrides(): ScriptNotificationOverrides? {
        if (!hasAnyOverridesEnabled()) return null

        val discordOverrides =
            if (discordOverrideEnabled.value && discordWebhookUrl.text.isNotBlank()) {
                DiscordOverrides(webhookUrl = discordWebhookUrl.text)
            } else {
                null
            }

        val telegramOverrides =
            if (
                telegramOverrideEnabled.value &&
                telegramBotToken.text.isNotBlank() &&
                telegramChatId.text.isNotBlank()
            ) {
                TelegramOverrides(
                    botToken = telegramBotToken.text,
                    chatId = telegramChatId.text,
                )
            } else {
                null
            }

        val emailOverrides =
            if (emailOverrideEnabled.value && validateEmailFields()) {
                EmailOverrides(
                    smtpHost = emailSmtpHost.text,
                    smtpPort = emailSmtpPort.text.toIntOrNull() ?: 587,
                    username = emailUsername.text,
                    password = emailPassword.text,
                    from = emailFrom.text,
                    to = emailTo.text,
                    useTls = emailUseTls.value,
                )
            } else {
                null
            }

        if (discordOverrides == null && telegramOverrides == null && emailOverrides == null) {
            return null
        }

        return ScriptNotificationOverrides(
            discordOverrides = discordOverrides,
            telegramOverrides = telegramOverrides,
            emailOverrides = emailOverrides,
        )
    }

    private fun validateEmailFields(): Boolean =
        emailSmtpHost.text.isNotBlank() &&
            emailSmtpPort.text.toIntOrNull() != null &&
            emailUsername.text.isNotBlank() &&
            emailPassword.text.isNotBlank() &&
            emailFrom.text.isNotBlank() &&
            emailTo.text.isNotBlank()
}

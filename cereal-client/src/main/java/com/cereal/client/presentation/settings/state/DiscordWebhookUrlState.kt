package com.cereal.client.presentation.settings.state

import com.cereal.client.presentation.view.fields.state.StringTextFieldState
import com.cereal.client.presentation.view.fields.validator.RequiredStringFieldValidator
import com.cereal.client.presentation.view.fields.validator.StringFieldValidator
import java.util.Locale

class DiscordWebhookUrlState : StringTextFieldState(validators = listOf(RequiredStringFieldValidator(), DiscordWebhookUrlValidator()))

class DiscordWebhookUrlValidator : StringFieldValidator {
    override fun validate(value: String?): String? =
        try {
            if (value == null) error("Value is null")
            val parsedUrl = java.net.URI(value).toURL()
            val isHttps = parsedUrl.protocol.lowercase(Locale.getDefault()).contains("https", ignoreCase = true)
            val isDiscordHost = parsedUrl.host.lowercase(Locale.getDefault()).contains("discord.com", ignoreCase = true)
            val hasApiPath = parsedUrl.path.contains("api", ignoreCase = true)
            val hasWebhooksPath = parsedUrl.path.contains("webhooks", ignoreCase = true)
            val isDiscordWebhookHost = isHttps && isDiscordHost
            val isWebhookPath = hasApiPath && hasWebhooksPath
            if (isDiscordWebhookHost && isWebhookPath) {
                null
            } else {
                "Invalid Discord webhook url"
            }
        } catch (_: Exception) {
            "Invalid Discord webhook url"
        }
}

package com.cereal.client.application.interactor.notification

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.domain.repository.NotificationSettingsRepository

/**
 * Interactor that checks if any notification channels are configured.
 * Considers both global settings and script-specific overrides.
 *
 * Priority chain (highest to lowest):
 * 1. Script notification overrides - if enabled and has required values
 * 2. Global notification settings - if channel is enabled
 *
 * @return true if at least one notification channel is configured, false otherwise
 */
class HasNotificationChannelsConfiguredInteractor(
    private val notificationSettingsRepository: NotificationSettingsRepository,
) : Interactor<Boolean, HasNotificationChannelsConfiguredInteractor.Params>() {
    override suspend fun run(params: Params): Boolean {
        val settings = notificationSettingsRepository.getApplicationPreferenceSettings()

        // Check Discord: global enabled OR override enabled with webhook URL
        val hasDiscord =
            settings.discordWebhookEnabled ||
                params.scriptOverrides
                    ?.discordOverrides
                    ?.webhookUrl
                    ?.isNotBlank() == true

        // Check Telegram: global enabled OR override enabled with bot token and chat ID
        val hasTelegram =
            settings.telegramEnabled ||
                (
                    params.scriptOverrides?.telegramOverrides?.let {
                        it.botToken.isNotBlank() && it.chatId.isNotBlank()
                    } == true
                )

        // Check Email: global enabled OR override enabled with required fields
        val hasEmail =
            settings.emailEnabled ||
                (
                    params.scriptOverrides?.emailOverrides?.let {
                        it.smtpHost.isNotBlank() &&
                            it.from.isNotBlank() &&
                            it.to.isNotBlank()
                    } == true
                )

        return hasDiscord || hasTelegram || hasEmail
    }

    data class Params(
        val scriptOverrides: ScriptNotificationOverrides?,
    )
}

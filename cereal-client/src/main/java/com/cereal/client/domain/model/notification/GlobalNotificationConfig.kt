package com.cereal.client.domain.model.notification

/**
 * A snapshot of every global notification setting the [NotificationResolver] needs: per-channel
 * enabled flags and field values. Read once from settings by the interactor and passed into the
 * pure resolver, so the resolver itself performs no I/O.
 *
 * Field values are already normalized to `null` when unset (e.g. an empty stored string becomes
 * `null`), so the resolver's priority merge can use plain `?:` chains.
 */
data class GlobalNotificationConfig(
    val discord: Discord,
    val telegram: Telegram,
    val email: Email,
    val desktopEnabled: Boolean,
) {
    data class Discord(
        val enabled: Boolean,
        val webhookUrl: String?,
    )

    data class Telegram(
        val enabled: Boolean,
        val botToken: String?,
        val chatId: String?,
    )

    data class Email(
        val enabled: Boolean,
        val smtpHost: String?,
        val smtpPort: Int?,
        val username: String?,
        val password: String?,
        val from: String?,
        val to: String?,
        val useTls: Boolean,
    )
}

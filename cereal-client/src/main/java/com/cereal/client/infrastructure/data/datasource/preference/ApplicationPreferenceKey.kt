package com.cereal.client.infrastructure.data.datasource.preference

sealed class ApplicationPreferenceKey<T>(
    val key: String,
    val defaultValue: T,
) {
    object DesktopNotificationsEnabled :
        ApplicationPreferenceKey<Boolean>(KEY_DESKTOP_NOTIFICATIONS_ENABLED, true)

    object DiscordActivityStatusEnabled :
        ApplicationPreferenceKey<Boolean>(KEY_DISCORD_ACTIVITY_STATUS_ENABLED, true)

    object DiscordWebhookEnabled : ApplicationPreferenceKey<Boolean>(KEY_DISCORD_WEBHOOK_ENABLED, false)

    object DiscordWebhookUrl : ApplicationPreferenceKey<String>(KEY_DISCORD_WEBHOOK_URL, "")

    object UserAuthenticationToken :
        ApplicationPreferenceKey<String>(KEY_USER_AUTHENTICATION_TOKEN, "")

    object AppVersionCheckTime :
        ApplicationPreferenceKey<String>(KEY_APP_VERSION_CHECK_TIME, "")

    object DevelopmentScriptsEnabled :
        ApplicationPreferenceKey<Boolean>(KEY_DEVELOPMENT_SCRIPTS_ENABLED, false)

    object TelegramEnabled : ApplicationPreferenceKey<Boolean>(KEY_TELEGRAM_ENABLED, false)

    object TelegramBotToken : ApplicationPreferenceKey<String>(KEY_TELEGRAM_BOT_TOKEN, "")

    object TelegramChatId : ApplicationPreferenceKey<String>(KEY_TELEGRAM_CHAT_ID, "")

    object EmailEnabled : ApplicationPreferenceKey<Boolean>(KEY_EMAIL_ENABLED, false)

    object EmailSmtpHost : ApplicationPreferenceKey<String>(KEY_EMAIL_SMTP_HOST, "")

    object EmailSmtpPort : ApplicationPreferenceKey<Int>(KEY_EMAIL_SMTP_PORT, DEFAULT_SMTP_PORT)

    object EmailUsername : ApplicationPreferenceKey<String>(KEY_EMAIL_USERNAME, "")

    object EmailPassword : ApplicationPreferenceKey<String>(KEY_EMAIL_PASSWORD, "")

    object EmailFrom : ApplicationPreferenceKey<String>(KEY_EMAIL_FROM, "")

    object EmailTo : ApplicationPreferenceKey<String>(KEY_EMAIL_TO, "")

    object EmailUseTls : ApplicationPreferenceKey<Boolean>(KEY_EMAIL_USE_TLS, true)

    object ShowDebugLogs : ApplicationPreferenceKey<Boolean>(KEY_SHOW_DEBUG_LOGS, false)

    object ProxyHealthCheckInterval :
        ApplicationPreferenceKey<String>(KEY_PROXY_HEALTH_CHECK_INTERVAL, "off")

    object NotificationCenterLastSeenAt :
        ApplicationPreferenceKey<Long>(KEY_NOTIFICATION_CENTER_LAST_SEEN_AT, 0L)

    object MarsProxiesApiToken : ApplicationPreferenceKey<String>(KEY_MARS_PROXIES_API_TOKEN, "")

    companion object {
        const val KEY_DESKTOP_NOTIFICATIONS_ENABLED = "key_desktop_notifications_enabled"
        const val KEY_DISCORD_ACTIVITY_STATUS_ENABLED = "key_discord_activity_status_enabled"
        const val KEY_DISCORD_WEBHOOK_ENABLED = "key_discord_webhook_enabled"
        const val KEY_DISCORD_WEBHOOK_URL = "key_discord_webhook_url"
        const val KEY_USER_AUTHENTICATION_TOKEN = "key_user_authentication_token"
        const val KEY_APP_VERSION_CHECK_TIME = "key_aoo_version_check_time"
        const val KEY_DEVELOPMENT_SCRIPTS_ENABLED = "key_development_scripts_enabled"
        const val KEY_TELEGRAM_ENABLED = "key_telegram_enabled"
        const val KEY_TELEGRAM_BOT_TOKEN = "key_telegram_bot_token"
        const val KEY_TELEGRAM_CHAT_ID = "key_telegram_chat_id"
        const val KEY_EMAIL_ENABLED = "key_email_enabled"
        const val KEY_EMAIL_SMTP_HOST = "key_email_smtp_host"
        const val KEY_EMAIL_SMTP_PORT = "key_email_smtp_port"
        const val KEY_EMAIL_USERNAME = "key_email_username"
        const val KEY_EMAIL_PASSWORD = "key_email_password"
        const val KEY_EMAIL_FROM = "key_email_from"
        const val KEY_EMAIL_TO = "key_email_to"
        const val KEY_EMAIL_USE_TLS = "key_email_use_tls"
        const val KEY_SHOW_DEBUG_LOGS = "key_show_debug_logs"
        const val KEY_PROXY_HEALTH_CHECK_INTERVAL = "key_proxy_health_check_interval"
        const val KEY_NOTIFICATION_CENTER_LAST_SEEN_AT = "key_notification_center_last_seen_at"
        const val KEY_MARS_PROXIES_API_TOKEN = "key_mars_proxies_api_token"

        private const val DEFAULT_SMTP_PORT = 587

        /**
         * Keys whose values must never appear in log output.
         * Log statements must replace the value with a redacted placeholder.
         */
        val sensitiveKeys: Set<String> =
            setOf(
                KEY_USER_AUTHENTICATION_TOKEN,
                KEY_TELEGRAM_BOT_TOKEN,
                KEY_EMAIL_PASSWORD,
                KEY_DISCORD_WEBHOOK_URL,
                KEY_MARS_PROXIES_API_TOKEN,
            )
    }
}

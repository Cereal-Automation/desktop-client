package com.cereal.client.infrastructure.data.datasource.preference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Backward-compatibility corpus for [ApplicationPreferenceKey].
 *
 * Each fixture pins the **frozen** on-disk string for one preference key. These strings are the
 * lookup keys in the `SettingsEntity` key-value store: the value a user saved is retrievable only
 * if the code asks for it under the exact same key string on the next launch.
 *
 * ## Why this exists
 *
 * The key-value store has no schema and no migration mechanism for key *names*. Renaming a key
 * constant — even to fix an obvious typo — silently orphans every value previously stored under the
 * old name: the setting reverts to its default and the user's configuration is lost with no error.
 *
 * This is not hypothetical: [ApplicationPreferenceKey.KEY_APP_VERSION_CHECK_TIME] is literally
 * `"key_aoo_version_check_time"` (a typo for `app`). Because real installs already hold a value
 * under that exact string, the typo is now a frozen contract — "correcting" it would discard data.
 * This corpus makes that constraint explicit and fails the build on any accidental key rename.
 *
 * ## Rules for this file (read before editing)
 *
 * - **Never change an existing expected string to match a renamed key.** If a key constant changed
 *   and broke a fixture, the rename orphans user data — revert the rename (or add an explicit
 *   read-the-old-key migration), do not edit the fixture.
 * - When a genuinely new preference key is added, append a fixture pinning its string.
 * - When a key is **retired** (its feature removed), delete its fixture. Values left under the old
 *   string are inert — nothing reads them — so no migration is owed. Retiring differs from
 *   renaming: a rename keeps a live feature but loses its data.
 */
class ApplicationPreferenceKeyBackwardCompatibilityTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    fun `preference key string is frozen`(fixture: Fixture) {
        assertEquals(fixture.expectedKey, fixture.actualKey) {
            "Backward-compatibility break: a preference key string changed. Values stored under the " +
                "old key would be silently orphaned (revert to default). Restore the original string " +
                "or add an explicit migration that reads the old key."
        }
    }

    data class Fixture(
        val name: String,
        val actualKey: String,
        val expectedKey: String,
    ) {
        override fun toString(): String = name
    }

    companion object {
        // ---- FROZEN PREFERENCE KEY STRINGS — DO NOT EDIT TO MATCH A RENAME (see class kdoc) ----
        @JvmStatic
        fun corpus(): List<Arguments> =
            listOf(
                fixture("DesktopNotificationsEnabled", ApplicationPreferenceKey.DesktopNotificationsEnabled.key, "key_desktop_notifications_enabled"),
                fixture("DiscordActivityStatusEnabled", ApplicationPreferenceKey.DiscordActivityStatusEnabled.key, "key_discord_activity_status_enabled"),
                fixture("DiscordWebhookEnabled", ApplicationPreferenceKey.DiscordWebhookEnabled.key, "key_discord_webhook_enabled"),
                fixture("DiscordWebhookUrl", ApplicationPreferenceKey.DiscordWebhookUrl.key, "key_discord_webhook_url"),
                fixture("UserAuthenticationToken", ApplicationPreferenceKey.UserAuthenticationToken.key, "key_user_authentication_token"),
                // Frozen typo: "aoo" is intentional — real installs hold values under this exact string.
                fixture("AppVersionCheckTime", ApplicationPreferenceKey.AppVersionCheckTime.key, "key_aoo_version_check_time"),
                fixture("DevelopmentScriptsEnabled", ApplicationPreferenceKey.DevelopmentScriptsEnabled.key, "key_development_scripts_enabled"),
                fixture("TelegramEnabled", ApplicationPreferenceKey.TelegramEnabled.key, "key_telegram_enabled"),
                fixture("TelegramBotToken", ApplicationPreferenceKey.TelegramBotToken.key, "key_telegram_bot_token"),
                fixture("TelegramChatId", ApplicationPreferenceKey.TelegramChatId.key, "key_telegram_chat_id"),
                fixture("EmailEnabled", ApplicationPreferenceKey.EmailEnabled.key, "key_email_enabled"),
                fixture("EmailSmtpHost", ApplicationPreferenceKey.EmailSmtpHost.key, "key_email_smtp_host"),
                fixture("EmailSmtpPort", ApplicationPreferenceKey.EmailSmtpPort.key, "key_email_smtp_port"),
                fixture("EmailUsername", ApplicationPreferenceKey.EmailUsername.key, "key_email_username"),
                fixture("EmailPassword", ApplicationPreferenceKey.EmailPassword.key, "key_email_password"),
                fixture("EmailFrom", ApplicationPreferenceKey.EmailFrom.key, "key_email_from"),
                fixture("EmailTo", ApplicationPreferenceKey.EmailTo.key, "key_email_to"),
                fixture("EmailUseTls", ApplicationPreferenceKey.EmailUseTls.key, "key_email_use_tls"),
                fixture("ShowDebugLogs", ApplicationPreferenceKey.ShowDebugLogs.key, "key_show_debug_logs"),
                fixture("ProxyHealthCheckInterval", ApplicationPreferenceKey.ProxyHealthCheckInterval.key, "key_proxy_health_check_interval"),
            )

        private fun fixture(
            name: String,
            actualKey: String,
            expectedKey: String,
        ): Arguments = Arguments.of(Fixture(name, actualKey, expectedKey))
    }
}

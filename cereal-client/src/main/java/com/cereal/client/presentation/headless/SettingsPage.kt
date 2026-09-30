package com.cereal.client.presentation.headless

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.auth.GetAuthenticatedUserInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.settings.GetApplicationPreferencesSettingsInteractor
import com.cereal.client.application.interactor.settings.developers.SetDevelopmentScriptsInteractor
import com.cereal.client.application.interactor.settings.developers.SetShowDebugLogsInteractor
import com.cereal.client.application.interactor.settings.notifications.SaveAllNotificationSettingsInteractor
import com.cereal.client.application.interactor.settings.notifications.SendNotificationTestMessageInteractor
import com.cereal.client.application.interactor.settings.privacy.GetCrashReportingEnabledInteractor
import com.cereal.client.application.interactor.settings.privacy.SetCrashReportingEnabledInteractor
import com.cereal.client.application.interactor.settings.proxy.SetProxyHealthCheckIntervalInteractor
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.client.domain.model.settings.ApplicationPreferenceSettings
import com.cereal.client.domain.model.settings.ProxyHealthCheckInterval
import com.cereal.client.presentation.headless.FieldForm.Editor
import com.cereal.client.presentation.headless.FieldForm.Field
import com.cereal.client.presentation.headless.FieldForm.Header
import com.github.kittinunf.result.coroutines.SuspendableResult
import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Key
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Tab `4`: the desktop's Settings sections on a [FieldForm]. Every field saves when committed, with a
 * flash line for the result; `T` sends a test message on the focused channel.
 *
 * Headless differences: no desktop-notification toggle (the channel is forced off, see
 * `HeadlessModule`), links and the logs directory are printed, Discord activity status is shown as
 * unavailable and "My scripts" is a read-only count.
 */
@OptIn(FlowPreview::class)
@Suppress("LongParameterList")
class SettingsPage(
    private val scope: CoroutineScope,
    private val repaint: () -> Unit,
    private val config: ApplicationConfig,
    getAuthenticatedUserInteractor: GetAuthenticatedUserInteractor,
    private val getApplicationPreferencesSettingsInteractor: GetApplicationPreferencesSettingsInteractor,
    private val getScriptsInteractor: GetScriptsInteractor,
    private val getCrashReportingEnabledInteractor: GetCrashReportingEnabledInteractor,
    private val saveAllNotificationSettingsInteractor: SaveAllNotificationSettingsInteractor,
    private val sendNotificationTestMessageInteractor: SendNotificationTestMessageInteractor,
    private val setProxyHealthCheckIntervalInteractor: SetProxyHealthCheckIntervalInteractor,
    private val setCrashReportingEnabledInteractor: SetCrashReportingEnabledInteractor,
    private val setDevelopmentScriptsInteractor: SetDevelopmentScriptsInteractor,
    private val setShowDebugLogsInteractor: SetShowDebugLogsInteractor,
) : TuiPage {
    private enum class Channel(
        val label: String,
    ) {
        DISCORD("Discord"),
        TELEGRAM("Telegram"),
        EMAIL("Email"),
    }

    /** The stored settings, or null while signed out (the notification store is user-scoped). */
    @Volatile
    private var settings: ApplicationPreferenceSettings? = null

    @Volatile
    private var scripts = 0

    @Volatile
    private var crashReporting = true

    /** The last save or test result, cleared by the next key. */
    @Volatile
    private var flash: String? = null

    private val form = FieldForm(onChange = ::onChange)

    override val title = "Settings"

    override val keys: String
        get() = listOfNotNull(form.keys, if (!form.editing && focusedChannel() != null) "T test" else null).joinToString(" · ")

    init {
        scope.launch {
            getAuthenticatedUserInteractor(Interactor.None())
                .map { (it as? SuspendableResult.Success)?.value != null }
                .distinctUntilChanged()
                .collectLatest { signedIn ->
                    settings = null
                    repaint()
                    if (signedIn) observe()
                }
        }
    }

    private suspend fun observe() =
        coroutineScope {
            getCrashReportingEnabledInteractor(Interactor.None()) { result ->
                if (result is SuspendableResult.Success) crashReporting = result.value
            }
            launch {
                getScriptsInteractor(Interactor.None()).collect { result ->
                    if (result is SuspendableResult.Success) scripts = result.value.size
                    repaint()
                }
            }
            getApplicationPreferencesSettingsInteractor(Interactor.None()).collect { result ->
                if (result is SuspendableResult.Success) settings = result.value
                repaint()
            }
        }

    /** The banner line when Discord, Telegram and email are all disabled (enabled flags only). */
    fun noChannelBanner(): String? = if (settings?.hasNoExternalNotificationChannels() == true) NO_CHANNEL else null

    override fun body(
        width: Int,
        height: Int,
    ): List<String> {
        val settings = settings ?: return listOf("", "  Loading…")
        form.rows = rows(settings)
        val flash = flash?.let { listOf("", "  $it") }.orEmpty()
        return form.render(width, (height - flash.size).coerceAtLeast(0)) + flash
    }

    override fun onKey(key: Key): Boolean {
        if (settings == null) return false
        if (!form.editing) flash = null
        if (form.onKey(key)) return true
        val char = (key as? CharKey)?.char
        if (char != 'T' && char != 't') return false
        focusedChannel()?.let(::sendTest) ?: return false
        return true
    }

    private fun rows(s: ApplicationPreferenceSettings): List<FieldForm.Row> =
        buildList {
            add(Header("general", "General"))
            if (!config.isBranded) add(Field("scripts", "My scripts", null, Editor.None, display = "$scripts installed"))
            add(
                Field(
                    "activityStatus",
                    "Discord status",
                    null,
                    Editor.None,
                    display = "unavailable",
                    description = "Discord activity status needs the desktop client.",
                ),
            )
            add(Field("version", "Version", null, Editor.None, display = config.versionName))

            add(Header("notifications", "Notifications"))
            add(toggle("discord", "Discord", s.discordWebhookEnabled, "Discord webhook notifications. T sends a test."))
            if (s.discordWebhookEnabled) add(secret("discordWebhookUrl", "  Webhook URL", s.discordWebhookUrl))
            add(toggle("telegram", "Telegram", s.telegramEnabled, "Telegram notifications. T sends a test."))
            if (s.telegramEnabled) {
                add(secret("telegramBotToken", "  Bot token", s.telegramBotToken))
                add(text("telegramChatId", "  Chat ID", s.telegramChatId))
            }
            add(toggle("email", "Email", s.emailEnabled, "Email notifications via SMTP. T sends a test."))
            if (s.emailEnabled) {
                add(text("emailSmtpHost", "  SMTP host", s.emailSmtpHost))
                add(Field("emailSmtpPort", "  SMTP port", ConfigValue.IntValue(s.emailSmtpPort), Editor.Line("port", ::parsePort)))
                add(text("emailUsername", "  Username", s.emailUsername))
                add(secret("emailPassword", "  Password", s.emailPassword))
                add(text("emailFrom", "  From", s.emailFrom))
                add(text("emailTo", "  To", s.emailTo))
                add(toggle("emailUseTls", "  Use TLS", s.emailUseTls))
            }

            add(Header("contact", "Get in touch!"))
            add(Field("community", "Discord", null, Editor.None, display = config.discordUrl))
            add(Field("github", "GitHub", null, Editor.None, display = config.githubUrl))
            add(Field("website", "Website", null, Editor.None, display = config.websiteUrl))
            // The description repeats the path, with room for a long one.
            val logs = config.logsDirectory.path
            add(Field("logs", "Logs directory", null, Editor.None, display = logs, description = logs))

            add(Header("proxies", "Proxies"))
            add(
                Field(
                    "healthCheck",
                    "Background checks",
                    ConfigValue.EnumValue(s.proxyHealthCheckInterval),
                    Editor.Cycle(ProxyHealthCheckInterval.entries.map { ConfigValue.EnumValue(it) }, nullable = false),
                    display = "‹ ${s.proxyHealthCheckInterval.label()} ›",
                    description = "How often saved proxies are re-probed in the background.",
                ),
            )

            add(Header("privacy", "Privacy"))
            add(toggle("crashReporting", "Send crash reports", crashReporting, "Crash reports go to Sentry; never script contents, passwords or proxies."))

            add(Header("developers", "Developers"))
            add(toggle("developmentScripts", "Development scripts", s.developmentScriptsEnabled, "Show and use your team's draft scripts."))
            add(toggle("debugLogs", "Debug logs", s.showDebugLogs, "Show DEBUG-level entries in the task log."))
        }

    private fun onChange(
        field: Field,
        value: ConfigValue?,
    ) {
        val on = (value as? ConfigValue.BooleanValue)?.raw == true
        val text = value?.raw?.let { (it as? Secret)?.reveal() ?: it.toString() }.orEmpty()
        val label = field.label.trim()
        val params =
            when (field.key) {
                "discord" -> SaveAllNotificationSettingsInteractor.Params(discordEnabled = on)
                "discordWebhookUrl" -> SaveAllNotificationSettingsInteractor.Params(discordWebhookUrl = text)
                "telegram" -> SaveAllNotificationSettingsInteractor.Params(telegramEnabled = on)
                "telegramBotToken" -> SaveAllNotificationSettingsInteractor.Params(telegramBotToken = text)
                "telegramChatId" -> SaveAllNotificationSettingsInteractor.Params(telegramChatId = text)
                "email" -> SaveAllNotificationSettingsInteractor.Params(emailEnabled = on)
                "emailSmtpHost" -> SaveAllNotificationSettingsInteractor.Params(emailSmtpHost = text)
                "emailSmtpPort" -> SaveAllNotificationSettingsInteractor.Params(emailSmtpPort = (value as? ConfigValue.IntValue)?.raw ?: DEFAULT_SMTP_PORT)
                "emailUsername" -> SaveAllNotificationSettingsInteractor.Params(emailUsername = text)
                "emailPassword" -> SaveAllNotificationSettingsInteractor.Params(emailPassword = text)
                "emailFrom" -> SaveAllNotificationSettingsInteractor.Params(emailFrom = text)
                "emailTo" -> SaveAllNotificationSettingsInteractor.Params(emailTo = text)
                "emailUseTls" -> SaveAllNotificationSettingsInteractor.Params(emailUseTls = on)
                else -> null
            }
        when {
            params != null -> {
                run(label, saveAllNotificationSettingsInteractor, params)
            }

            field.key == "healthCheck" -> {
                val interval = (value as ConfigValue.EnumValue).raw as ProxyHealthCheckInterval
                run(label, setProxyHealthCheckIntervalInteractor, SetProxyHealthCheckIntervalInteractor.Params(interval))
            }

            field.key == "crashReporting" -> {
                // Nothing publishes this setting, so it is set optimistically, as on the desktop.
                crashReporting = on
                run(label, setCrashReportingEnabledInteractor, SetCrashReportingEnabledInteractor.Params(on))
            }

            field.key == "developmentScripts" -> {
                run(label, setDevelopmentScriptsInteractor, SetDevelopmentScriptsInteractor.Params(on))
            }

            field.key == "debugLogs" -> {
                run(label, setShowDebugLogsInteractor, SetShowDebugLogsInteractor.Params(on))
            }
        }
    }

    private fun <P> run(
        label: String,
        interactor: Interactor<Unit, P>,
        params: P,
    ) {
        scope.launch {
            interactor(params) { result ->
                flash =
                    when (result) {
                        is SuspendableResult.Success -> "✓ Saved $label."
                        is SuspendableResult.Failure -> "! $label not saved: ${result.error.message}"
                    }
                repaint()
            }
        }
    }

    /** The channel the focused field belongs to (its toggle or one of its fields). */
    private fun focusedChannel(): Channel? {
        val key = form.selected?.key ?: return null
        return Channel.entries.firstOrNull { key.startsWith(it.name.lowercase()) }
    }

    /** Sends a test with the stored values (every field is saved on commit). */
    private fun sendTest(channel: Channel) {
        val s = settings ?: return
        val params =
            when (channel) {
                Channel.DISCORD -> {
                    SendNotificationTestMessageInteractor.Params.Discord(s.discordWebhookUrl.orEmpty())
                }

                Channel.TELEGRAM -> {
                    SendNotificationTestMessageInteractor.Params.Telegram(s.telegramBotToken.orEmpty(), s.telegramChatId.orEmpty())
                }

                Channel.EMAIL -> {
                    SendNotificationTestMessageInteractor.Params.Email(
                        smtpHost = s.emailSmtpHost.orEmpty(),
                        smtpPort = s.emailSmtpPort,
                        username = s.emailUsername.orEmpty(),
                        password = s.emailPassword.orEmpty(),
                        from = s.emailFrom.orEmpty(),
                        to = s.emailTo.orEmpty(),
                        useTls = s.emailUseTls,
                    )
                }
            }
        flash = "Sending a test message on ${channel.label}…"
        scope.launch {
            sendNotificationTestMessageInteractor(params) { result ->
                flash =
                    when (result) {
                        is SuspendableResult.Success -> "✓ Test message sent on ${channel.label}."
                        is SuspendableResult.Failure -> "! ${channel.label} test failed: ${result.error.message}"
                    }
                repaint()
            }
        }
    }

    private companion object {
        const val NO_CHANNEL = "No notification channel set up. Add Discord, Telegram or email on tab 4."
        const val DEFAULT_SMTP_PORT = 587
        const val MAX_PORT = 65535

        fun toggle(
            key: String,
            label: String,
            value: Boolean,
            description: String = "",
        ) = Field(key, label, ConfigValue.BooleanValue(value), Editor.Toggle, description = description)

        fun text(
            key: String,
            label: String,
            value: String?,
        ) = Field(key, label, value?.takeIf { it.isNotEmpty() }?.let { ConfigValue.StringValue(it) }, Editor.Line("text") { ConfigValue.StringValue(it) })

        fun secret(
            key: String,
            label: String,
            value: String?,
        ) = Field(key, label, value?.takeIf { it.isNotEmpty() }?.let { ConfigValue.SecretValue(Secret(it)) }, Editor.Secret)

        fun parsePort(text: String): ConfigValue =
            text
                .trim()
                .toIntOrNull()
                ?.takeIf { it in 1..MAX_PORT }
                ?.let { ConfigValue.IntValue(it) }
                ?: throw IllegalArgumentException("Enter a port from 1 to $MAX_PORT.")

        fun ProxyHealthCheckInterval.label() =
            when (this) {
                ProxyHealthCheckInterval.OFF -> "Off"
                ProxyHealthCheckInterval.EVERY_6_HOURS -> "Every 6h"
                ProxyHealthCheckInterval.EVERY_24_HOURS -> "Every 24h"
            }
    }
}

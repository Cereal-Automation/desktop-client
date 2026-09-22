package com.cereal.client.domain.model.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure tests for the channel-selection, priority, and validation rules. No repositories or fakes:
 * the resolver takes a [GlobalNotificationConfig] snapshot and returns [ChannelResolution]s.
 */
class NotificationResolverTest {
    private val resolver = NotificationResolver()

    private fun config(
        discordEnabled: Boolean = false,
        discordWebhookUrl: String? = null,
        telegramEnabled: Boolean = false,
        telegramBotToken: String? = null,
        telegramChatId: String? = null,
        emailEnabled: Boolean = false,
        emailSmtpHost: String? = null,
        emailSmtpPort: Int? = null,
        emailFrom: String? = null,
        emailTo: String? = null,
        desktopEnabled: Boolean = false,
    ) = GlobalNotificationConfig(
        discord = GlobalNotificationConfig.Discord(discordEnabled, discordWebhookUrl),
        telegram = GlobalNotificationConfig.Telegram(telegramEnabled, telegramBotToken, telegramChatId),
        email =
            GlobalNotificationConfig.Email(
                enabled = emailEnabled,
                smtpHost = emailSmtpHost,
                smtpPort = emailSmtpPort,
                username = null,
                password = null,
                from = emailFrom,
                to = emailTo,
                useTls = true,
            ),
        desktopEnabled = desktopEnabled,
    )

    private val basic = ScriptNotification(title = "Title", message = "Message")

    private fun resolved(
        resolutions: List<ChannelResolution>,
        channel: NotificationChannelType,
    ): ChannelResolution.Resolved = resolutions.filterIsInstance<ChannelResolution.Resolved>().single { it.channel == channel }

    // --- channel selection ---

    @Test
    fun `a disabled channel with no override is omitted entirely`() {
        val resolutions = resolver.resolve(basic, overrides = null, config = config())
        assertTrue(resolutions.isEmpty())
    }

    @Test
    fun `an enabled channel is in play`() {
        val resolutions = resolver.resolve(basic, null, config(desktopEnabled = true))
        assertEquals(listOf(NotificationChannelType.SYSTEM), resolutions.map { it.channel })
    }

    @Test
    fun `a globally disabled channel is in play when the script overrides it`() {
        val overrides = ScriptNotificationOverrides(discordOverrides = DiscordOverrides(webhookUrl = "https://discord.com/api/webhooks/1/a"))
        val resolutions = resolver.resolve(basic, overrides, config(discordEnabled = false))
        val discord = resolved(resolutions, NotificationChannelType.DISCORD).data as DiscordNotificationData
        assertEquals("https://discord.com/api/webhooks/1/a", discord.webhookUrl)
    }

    @Test
    fun `channels are resolved in a stable order discord telegram email system`() {
        val resolutions =
            resolver.resolve(
                basic,
                null,
                config(
                    discordEnabled = true,
                    discordWebhookUrl = "https://discord.com/api/webhooks/1/a",
                    telegramEnabled = true,
                    telegramBotToken = "123:token",
                    telegramChatId = "123",
                    emailEnabled = true,
                    emailSmtpHost = "smtp.example.com",
                    emailSmtpPort = 587,
                    emailFrom = "from@example.com",
                    emailTo = "to@example.com",
                    desktopEnabled = true,
                ),
            )
        assertEquals(
            listOf(
                NotificationChannelType.DISCORD,
                NotificationChannelType.TELEGRAM,
                NotificationChannelType.EMAIL,
                NotificationChannelType.SYSTEM,
            ),
            resolutions.map { it.channel },
        )
    }

    // --- defaulting ---

    @Test
    fun `discord defaults to bold title and message when no payload supplied`() {
        val resolutions = resolver.resolve(basic, null, config(discordEnabled = true, discordWebhookUrl = "https://discord.com/api/webhooks/1/a"))
        val data = resolved(resolutions, NotificationChannelType.DISCORD).data as DiscordNotificationData
        assertEquals("**Title**\nMessage", data.content)
    }

    @Test
    fun `telegram defaults to markdown formatting`() {
        val resolutions =
            resolver.resolve(basic, null, config(telegramEnabled = true, telegramBotToken = "123:token", telegramChatId = "123"))
        val data = resolved(resolutions, NotificationChannelType.TELEGRAM).data as TelegramNotificationData
        assertEquals("*Title*\nMessage", data.text)
        assertEquals(TelegramParseMode.MARKDOWN, data.parseMode)
    }

    @Test
    fun `email subject defaults to the title`() {
        val resolutions =
            resolver.resolve(
                basic,
                null,
                config(emailEnabled = true, emailSmtpHost = "smtp.example.com", emailSmtpPort = 587, emailFrom = "a@b.com", emailTo = "c@d.com"),
            )
        val data = resolved(resolutions, NotificationChannelType.EMAIL).data as EmailNotificationData
        assertEquals("Title", data.subject)
        assertEquals("Message", data.body)
    }

    // --- channel override priority: script > override > global ---

    @Test
    fun `script value wins over override and global`() {
        val request =
            basic.copy(
                discordMessage = ScriptDiscordNotification(content = "c", webhookUrl = "https://discord.com/api/webhooks/script/h"),
            )
        val overrides = ScriptNotificationOverrides(discordOverrides = DiscordOverrides(webhookUrl = "https://discord.com/api/webhooks/override/h"))
        val resolutions = resolver.resolve(request, overrides, config(discordEnabled = true, discordWebhookUrl = "https://discord.com/api/webhooks/global/h"))
        val data = resolved(resolutions, NotificationChannelType.DISCORD).data as DiscordNotificationData
        assertEquals("https://discord.com/api/webhooks/script/h", data.webhookUrl)
    }

    @Test
    fun `override wins over global when script value absent`() {
        val overrides = ScriptNotificationOverrides(discordOverrides = DiscordOverrides(webhookUrl = "https://discord.com/api/webhooks/override/h"))
        val resolutions = resolver.resolve(basic, overrides, config(discordEnabled = true, discordWebhookUrl = "https://discord.com/api/webhooks/global/h"))
        val data = resolved(resolutions, NotificationChannelType.DISCORD).data as DiscordNotificationData
        assertEquals("https://discord.com/api/webhooks/override/h", data.webhookUrl)
    }

    @Test
    fun `global value is used when neither script nor override supply it`() {
        val resolutions = resolver.resolve(basic, null, config(discordEnabled = true, discordWebhookUrl = "https://discord.com/api/webhooks/global/h"))
        val data = resolved(resolutions, NotificationChannelType.DISCORD).data as DiscordNotificationData
        assertEquals("https://discord.com/api/webhooks/global/h", data.webhookUrl)
    }

    @Test
    fun `email merges each field independently across the three layers`() {
        val overrides =
            ScriptNotificationOverrides(
                emailOverrides =
                    EmailOverrides(
                        smtpHost = "smtp.override.com",
                        smtpPort = 25,
                        username = "u",
                        password = "p",
                        from = "override@from.com",
                        to = "override@to.com",
                        useTls = false,
                    ),
            )
        // Script supplies only `to`; override supplies host/from; global supplies port.
        val request = basic.copy(emailMessage = ScriptEmailNotification(subject = "S", body = "B", to = "script@to.com"))
        val resolutions =
            resolver.resolve(request, overrides, config(emailEnabled = false, emailSmtpPort = 999))
        val data = resolved(resolutions, NotificationChannelType.EMAIL).data as EmailNotificationData
        assertEquals("script@to.com", data.to) // script wins
        assertEquals("override@from.com", data.from) // override wins (no script value)
        assertEquals("smtp.override.com", data.smtpHost) // override wins
        assertEquals(25, data.smtpPort) // override wins over global
    }

    // --- validation -> MissingConfig (never throws, never aborts other channels) ---

    @Test
    fun `missing discord webhook across all layers yields MissingConfig`() {
        val resolutions = resolver.resolve(basic, null, config(discordEnabled = true, discordWebhookUrl = null))
        val unresolved = resolutions.single() as ChannelResolution.MissingConfig
        assertEquals(NotificationChannelType.DISCORD, unresolved.channel)
        assertTrue(unresolved.reason.contains("webhook"))
    }

    @Test
    fun `telegram reports the specific missing field`() {
        val resolutions = resolver.resolve(basic, null, config(telegramEnabled = true, telegramBotToken = "123:token", telegramChatId = null))
        val unresolved = resolutions.single() as ChannelResolution.MissingConfig
        assertTrue(unresolved.reason.contains("chat ID"))
    }

    @Test
    fun `a value rejected by the channel's own invariants yields MissingConfig rather than throwing`() {
        // 'not-an-email' fails EmailNotificationData's init validation.
        val resolutions =
            resolver.resolve(
                basic,
                null,
                config(emailEnabled = true, emailSmtpHost = "smtp.example.com", emailSmtpPort = 587, emailFrom = "not-an-email", emailTo = "c@d.com"),
            )
        assertTrue(resolutions.single() is ChannelResolution.MissingConfig)
    }

    @Test
    fun `one channel failing resolution does not affect another`() {
        val resolutions =
            resolver.resolve(basic, null, config(discordEnabled = true, discordWebhookUrl = null, desktopEnabled = true))
        assertTrue(resolved(resolutions, NotificationChannelType.SYSTEM).data is SystemNotificationData)
        assertTrue(resolutions.any { it is ChannelResolution.MissingConfig && it.channel == NotificationChannelType.DISCORD })
    }

    // --- payload preview ---

    @Test
    fun `resolved payload preview is the channel's public text`() {
        val resolutions = resolver.resolve(basic, null, config(desktopEnabled = true))
        assertEquals("Title\nMessage", resolved(resolutions, NotificationChannelType.SYSTEM).payloadPreview)
    }

    @Test
    fun `system notification with a null title previews just the message`() {
        val resolutions = resolver.resolve(ScriptNotification(title = null, message = "Only"), null, config(desktopEnabled = true))
        val system = resolved(resolutions, NotificationChannelType.SYSTEM)
        assertEquals("Only", system.payloadPreview)
        assertNull((system.data as SystemNotificationData).title)
    }
}

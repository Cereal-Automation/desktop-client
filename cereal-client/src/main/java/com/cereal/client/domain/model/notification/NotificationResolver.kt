package com.cereal.client.domain.model.notification

/**
 * Pure domain rule that turns a [ScriptNotification] into a list of per-channel [ChannelResolution]s.
 *
 * It owns three concerns that previously lived, interleaved, in the send interactor:
 * - **Channel selection** — a channel is in play when it is globally enabled, or the script
 *   provided an override for it (system/desktop has no override path).
 * - **Channel override priority** — each field is resolved `script value > override > global setting`.
 * - **Validation** — a required field missing from all three layers (or a value rejected by the
 *   channel's own [Notification] invariants) yields [ChannelResolution.MissingConfig] rather than a
 *   thrown exception, so one channel never aborts the others.
 *
 * Performs no I/O: global settings arrive as a [GlobalNotificationConfig] snapshot.
 */
class NotificationResolver {
    fun resolve(
        request: ScriptNotification,
        overrides: ScriptNotificationOverrides?,
        config: GlobalNotificationConfig,
    ): List<ChannelResolution> =
        buildList {
            if (config.discord.enabled || overrides?.discordOverrides != null) {
                add(resolveDiscord(request, overrides, config.discord))
            }
            if (config.telegram.enabled || overrides?.telegramOverrides != null) {
                add(resolveTelegram(request, overrides, config.telegram))
            }
            if (config.email.enabled || overrides?.emailOverrides != null) {
                add(resolveEmail(request, overrides, config.email))
            }
            if (config.desktopEnabled) {
                add(resolveSystem(request))
            }
        }

    private fun resolveDiscord(
        request: ScriptNotification,
        overrides: ScriptNotificationOverrides?,
        config: GlobalNotificationConfig.Discord,
    ): ChannelResolution {
        val payload = request.discordMessage ?: request.toDiscord()
        val webhookUrl =
            payload.webhookUrl
                ?: overrides?.discordOverrides?.webhookUrl
                ?: config.webhookUrl
                ?: return ChannelResolution.MissingConfig(
                    NotificationChannelType.DISCORD,
                    "Discord webhook URL is missing. It must be provided in the script, script settings, or global settings.",
                )

        return build(NotificationChannelType.DISCORD) {
            DiscordNotificationData(
                username = payload.username,
                content = payload.content,
                avatarUrl = payload.avatarUrl,
                tts = payload.tts,
                embeds = payload.embeds,
                webhookUrl = webhookUrl,
            ) to payload.content
        }
    }

    private fun resolveTelegram(
        request: ScriptNotification,
        overrides: ScriptNotificationOverrides?,
        config: GlobalNotificationConfig.Telegram,
    ): ChannelResolution {
        val payload = request.telegramMessage ?: request.toTelegram()
        val botToken =
            payload.botToken
                ?: overrides?.telegramOverrides?.botToken
                ?: config.botToken
                ?: return ChannelResolution.MissingConfig(
                    NotificationChannelType.TELEGRAM,
                    "Telegram bot token is missing. It must be provided in the script, script settings, or global settings.",
                )
        val chatId =
            payload.chatId
                ?: overrides?.telegramOverrides?.chatId
                ?: config.chatId
                ?: return ChannelResolution.MissingConfig(
                    NotificationChannelType.TELEGRAM,
                    "Telegram chat ID is missing. It must be provided in the script, script settings, or global settings.",
                )

        return build(NotificationChannelType.TELEGRAM) {
            TelegramNotificationData(
                chatId = chatId,
                text = payload.text,
                parseMode = payload.parseMode,
                disableWebPagePreview = payload.disableWebPagePreview,
                disableNotification = payload.disableNotification,
                replyToMessageId = payload.replyToMessageId,
                botToken = botToken,
            ) to payload.text
        }
    }

    private fun resolveEmail(
        request: ScriptNotification,
        overrides: ScriptNotificationOverrides?,
        config: GlobalNotificationConfig.Email,
    ): ChannelResolution {
        val payload = request.emailMessage ?: request.toEmail()
        val override = overrides?.emailOverrides

        val smtpHost = firstNonNull(payload.smtpHost, override?.smtpHost, config.smtpHost)
        val smtpPort = firstNonNull(payload.smtpPort, override?.smtpPort, config.smtpPort)
        val from = firstNonNull(payload.from, override?.from, config.from)
        val to = firstNonNull(payload.to, override?.to, config.to)
        val username = firstNonNull(payload.username, override?.username, config.username)
        val password = firstNonNull(payload.password, override?.password, config.password)
        val useTls = payload.useTls ?: override?.useTls ?: config.useTls

        // Single exit: each missing required field is reported once; the else branch smart-casts the
        // validated values to non-null, so no `!!` is needed.
        return when {
            smtpHost == null -> {
                missingConfigEmail("SMTP host")
            }

            smtpPort == null -> {
                missingConfigEmail("SMTP port")
            }

            from == null -> {
                missingConfigEmail("'from' address")
            }

            to == null -> {
                missingConfigEmail("'to' address")
            }

            else -> {
                build(NotificationChannelType.EMAIL) {
                    EmailNotificationData(
                        to = to,
                        from = from,
                        subject = payload.subject,
                        body = payload.body,
                        smtpHost = smtpHost,
                        smtpPort = smtpPort,
                        username = username,
                        password = password,
                        useTls = useTls,
                    ) to "${payload.subject}\n${payload.body}"
                }
            }
        }
    }

    private fun missingConfigEmail(field: String): ChannelResolution.MissingConfig =
        ChannelResolution.MissingConfig(
            NotificationChannelType.EMAIL,
            "Email $field is missing. It must be provided in the script, script settings, or global settings.",
        )

    private fun <T : Any> firstNonNull(vararg candidates: T?): T? = candidates.firstOrNull { it != null }

    private fun resolveSystem(request: ScriptNotification): ChannelResolution =
        build(NotificationChannelType.SYSTEM) {
            val data = SystemNotificationData(request.title, request.message)
            data to listOfNotNull(data.title, data.message).joinToString("\n")
        }

    /**
     * Runs [construct] (which builds the channel's [Notification] and its preview text) and maps a
     * validation failure from the data type's own invariants to [ChannelResolution.MissingConfig],
     * keeping [resolve] total.
     */
    private inline fun build(
        channel: NotificationChannelType,
        construct: () -> Pair<Notification, String?>,
    ): ChannelResolution =
        try {
            val (data, preview) = construct()
            ChannelResolution.Resolved(channel, data, preview)
        } catch (e: IllegalArgumentException) {
            ChannelResolution.MissingConfig(channel, e.message ?: "Invalid notification for $channel.")
        }

    private fun ScriptNotification.toDiscord(): ScriptDiscordNotification =
        ScriptDiscordNotification(
            content = if (title.isNullOrBlank()) message else "**$title**\n$message",
        )

    private fun ScriptNotification.toTelegram(): ScriptTelegramNotification =
        ScriptTelegramNotification(
            text = if (title.isNullOrBlank()) message else "*$title*\n$message",
            parseMode = TelegramParseMode.MARKDOWN,
        )

    private fun ScriptNotification.toEmail(): ScriptEmailNotification =
        ScriptEmailNotification(
            subject = title ?: "Notification",
            body = message,
        )
}

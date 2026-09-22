package com.cereal.client.domain.model.notification

/**
 * A script's request to notify: title, message, and optional per-channel payloads. Describes *what*
 * to send, independent of *how* it is delivered on any channel — the [NotificationResolver] turns
 * this (plus overrides and global settings) into channel-specific [Notification] data.
 */
data class ScriptNotification(
    val title: String?,
    val message: String,
    val discordMessage: ScriptDiscordNotification? = null,
    val telegramMessage: ScriptTelegramNotification? = null,
    val emailMessage: ScriptEmailNotification? = null,
)

data class ScriptDiscordNotification(
    val username: String? = null,
    val content: String? = null,
    val avatarUrl: String? = null,
    val tts: Boolean? = null,
    val embeds: List<DiscordEmbed> = emptyList(),
    val webhookUrl: String? = null,
)

data class ScriptTelegramNotification(
    val chatId: String? = null,
    val text: String,
    val parseMode: TelegramParseMode? = null,
    val disableWebPagePreview: Boolean? = null,
    val disableNotification: Boolean? = null,
    val replyToMessageId: Int? = null,
    val botToken: String? = null,
)

data class ScriptEmailNotification(
    val to: String? = null,
    val from: String? = null,
    val subject: String,
    val body: String,
    val smtpHost: String? = null,
    val smtpPort: Int? = null,
    val username: String? = null,
    val password: String? = null,
    val useTls: Boolean? = true,
)

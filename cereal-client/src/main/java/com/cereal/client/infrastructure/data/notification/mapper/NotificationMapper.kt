package com.cereal.client.infrastructure.data.notification.mapper

import com.cereal.client.domain.model.notification.AuthorEmbed as DomainAuthorEmbed
import com.cereal.client.domain.model.notification.DiscordEmbed as DomainDiscordEmbed
import com.cereal.client.domain.model.notification.DiscordNotificationData as DomainDiscordNotificationData
import com.cereal.client.domain.model.notification.EmailNotificationData as DomainEmailNotificationData
import com.cereal.client.domain.model.notification.FieldEmbed as DomainFieldEmbed
import com.cereal.client.domain.model.notification.FooterEmbed as DomainFooterEmbed
import com.cereal.client.domain.model.notification.ImageEmbed as DomainImageEmbed
import com.cereal.client.domain.model.notification.ProviderEmbed as DomainProviderEmbed
import com.cereal.client.domain.model.notification.TelegramNotificationData as DomainTelegramNotificationData
import com.cereal.client.domain.model.notification.TelegramParseMode as DomainTelegramParseMode
import com.cereal.client.domain.model.notification.ThumbnailEmbed as DomainThumbnailEmbed
import com.cereal.client.domain.model.notification.VideoEmbed as DomainVideoEmbed
import com.cereal.sdk.component.notification.discord.model.DiscordEmbed as SdkDiscordEmbed
import com.cereal.sdk.component.notification.discord.model.DiscordMessage as SdkDiscordMessage
import com.cereal.sdk.component.notification.discord.model.embed.AuthorEmbed as SdkAuthorEmbed
import com.cereal.sdk.component.notification.discord.model.embed.FieldEmbed as SdkFieldEmbed
import com.cereal.sdk.component.notification.discord.model.embed.FooterEmbed as SdkFooterEmbed
import com.cereal.sdk.component.notification.discord.model.embed.ImageEmbed as SdkImageEmbed
import com.cereal.sdk.component.notification.discord.model.embed.ProviderEmbed as SdkProviderEmbed
import com.cereal.sdk.component.notification.discord.model.embed.ThumbnailEmbed as SdkThumbnailEmbed
import com.cereal.sdk.component.notification.discord.model.embed.VideoEmbed as SdkVideoEmbed
import com.cereal.sdk.component.notification.email.model.EmailMessage as SdkEmailMessage
import com.cereal.sdk.component.notification.telegram.model.TelegramMessage as SdkTelegramMessage
import com.cereal.sdk.component.notification.telegram.model.TelegramParseMode as SdkTelegramParseMode

/**
 * Maps domain DiscordNotificationData to SDK DiscordMessage.
 */
fun DomainDiscordNotificationData.toSdkDiscordMessage(): SdkDiscordMessage =
    SdkDiscordMessage(
        username = this.username,
        content = this.content,
        avatarUrl = this.avatarUrl,
        tts = this.tts,
        embeds = this.embeds.map { it.toSdkDiscordEmbed() },
        webhookUrl = this.webhookUrl,
    )

/**
 * Maps domain DiscordEmbed to SDK DiscordEmbed.
 */
fun DomainDiscordEmbed.toSdkDiscordEmbed(): SdkDiscordEmbed =
    SdkDiscordEmbed(
        title = this.title,
        type = this.type,
        description = this.description,
        url = this.url,
        timestamp = this.timestamp,
        color = this.color,
        footer = this.footer?.toSdkFooterEmbed(),
        image = this.image?.toSdkImageEmbed(),
        thumbnail = this.thumbnail?.toSdkThumbnailEmbed(),
        video = this.video?.toSdkVideoEmbed(),
        provider = this.provider?.toSdkProviderEmbed(),
        author = this.author?.toSdkAuthorEmbed(),
        fields = this.fields?.map { it.toSdkFieldEmbed() },
    )

/**
 * Maps domain FooterEmbed to SDK FooterEmbed.
 */
fun DomainFooterEmbed.toSdkFooterEmbed(): SdkFooterEmbed =
    SdkFooterEmbed(
        text = this.text,
        iconUrl = this.iconUrl,
        proxyIconUrl = this.proxyIconUrl,
    )

/**
 * Maps domain ImageEmbed to SDK ImageEmbed.
 */
fun DomainImageEmbed.toSdkImageEmbed(): SdkImageEmbed =
    SdkImageEmbed(
        url = this.url,
        proxyUrl = this.proxyUrl,
        height = this.height,
        width = this.width,
    )

/**
 * Maps domain ThumbnailEmbed to SDK ThumbnailEmbed.
 */
fun DomainThumbnailEmbed.toSdkThumbnailEmbed(): SdkThumbnailEmbed =
    SdkThumbnailEmbed(
        url = this.url,
        proxyUrl = this.proxyUrl,
        height = this.height,
        width = this.width,
    )

/**
 * Maps domain VideoEmbed to SDK VideoEmbed.
 */
fun DomainVideoEmbed.toSdkVideoEmbed(): SdkVideoEmbed =
    SdkVideoEmbed(
        url = this.url,
        height = this.height,
        width = this.width,
    )

/**
 * Maps domain ProviderEmbed to SDK ProviderEmbed.
 */
fun DomainProviderEmbed.toSdkProviderEmbed(): SdkProviderEmbed =
    SdkProviderEmbed(
        name = this.name,
        url = this.url,
    )

/**
 * Maps domain AuthorEmbed to SDK AuthorEmbed.
 */
fun DomainAuthorEmbed.toSdkAuthorEmbed(): SdkAuthorEmbed =
    SdkAuthorEmbed(
        name = this.name,
        url = this.url,
        iconUrl = this.iconUrl,
        proxyIconUrl = this.proxyIconUrl,
    )

/**
 * Maps domain FieldEmbed to SDK FieldEmbed.
 */
fun DomainFieldEmbed.toSdkFieldEmbed(): SdkFieldEmbed =
    SdkFieldEmbed(
        name = this.name,
        value = this.value,
        inline = this.inline ?: false,
    )

/**
 * Maps domain TelegramNotificationData to SDK TelegramMessage.
 */
fun DomainTelegramNotificationData.toSdkTelegramMessage(): SdkTelegramMessage =
    SdkTelegramMessage(
        chatId = this.chatId,
        text = this.text,
        parseMode = this.parseMode?.toSdkTelegramParseMode(),
        disableWebPagePreview = this.disableWebPagePreview,
        disableNotification = this.disableNotification,
        replyToMessageId = this.replyToMessageId,
        botToken = this.botToken,
    )

/**
 * Maps domain TelegramParseMode to SDK TelegramParseMode.
 */
fun DomainTelegramParseMode.toSdkTelegramParseMode(): SdkTelegramParseMode =
    when (this) {
        DomainTelegramParseMode.MARKDOWN -> SdkTelegramParseMode.MARKDOWN
        DomainTelegramParseMode.MARKDOWN_V2 -> SdkTelegramParseMode.MARKDOWN_V2
        DomainTelegramParseMode.HTML -> SdkTelegramParseMode.HTML
    }

/**
 * Maps domain EmailNotificationData to SDK EmailMessage.
 */
fun DomainEmailNotificationData.toSdkEmailMessage(): SdkEmailMessage =
    SdkEmailMessage(
        to = this.to,
        from = this.from,
        subject = this.subject,
        body = this.body,
        smtpHost = this.smtpHost,
        smtpPort = this.smtpPort,
        username = this.username,
        password = this.password,
        useTls = this.useTls,
    )

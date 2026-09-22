package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.interactor.notification.SendNotificationFromScriptInstanceInteractor
import com.cereal.client.domain.model.notification.AuthorEmbed
import com.cereal.client.domain.model.notification.DiscordEmbed
import com.cereal.client.domain.model.notification.FieldEmbed
import com.cereal.client.domain.model.notification.FooterEmbed
import com.cereal.client.domain.model.notification.ImageEmbed
import com.cereal.client.domain.model.notification.ProviderEmbed
import com.cereal.client.domain.model.notification.ScriptDiscordNotification
import com.cereal.client.domain.model.notification.ScriptEmailNotification
import com.cereal.client.domain.model.notification.ScriptNotification
import com.cereal.client.domain.model.notification.ScriptTelegramNotification
import com.cereal.client.domain.model.notification.TelegramParseMode
import com.cereal.client.domain.model.notification.ThumbnailEmbed
import com.cereal.client.domain.model.notification.VideoEmbed
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.sdk.component.notification.NotificationComponent
import com.cereal.sdk.component.notification.Notification as SdkNotification
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

class NotificationComponentImpl(
    private val sendNotificationFromScriptInstanceInteractor: SendNotificationFromScriptInstanceInteractor,
    private val scriptPackageInstance: ScriptPackageInstance,
    private val taskId: String,
) : NotificationComponent {
    override suspend fun sendNotification(notification: SdkNotification) {
        val domainNotification =
            try {
                notification.toDomainNotification()
            } catch (e: IllegalArgumentException) {
                // The SDK notification is script-supplied and untrusted. Domain value objects (e.g. a
                // Discord FieldEmbed) enforce invariants such as a non-blank field name via require(...),
                // which throws IllegalArgumentException during this mapping — before the interactor's
                // protected boundary. Left raw, it escapes the script's execute() and TaskExecutor reports
                // it to Sentry as an unexpected crash. Re-raise as an expected, script-originated
                // CerealException so TaskExecutor surfaces the message to the user without Sentry noise.
                throw CerealException(
                    e.message ?: "The notification could not be sent because its content is invalid.",
                    e,
                )
            }
        sendNotificationFromScriptInstanceInteractor(
            SendNotificationFromScriptInstanceInteractor.Params(
                notification = domainNotification,
                taskId = taskId,
                scriptPackageInstance = scriptPackageInstance,
            ),
        )
    }

    private fun SdkNotification.toDomainNotification(): ScriptNotification =
        ScriptNotification(
            title = this.title,
            message = this.message,
            discordMessage = this.discordMessage?.toDomainDiscordNotificationData(),
            telegramMessage = this.telegramMessage?.toDomainTelegramNotificationData(),
            emailMessage = this.emailMessage?.toDomainEmailNotificationData(),
        )

    private fun SdkDiscordMessage.toDomainDiscordNotificationData(): ScriptDiscordNotification =
        ScriptDiscordNotification(
            username = this.username,
            content = this.content,
            avatarUrl = this.avatarUrl,
            tts = this.tts,
            embeds = this.embeds?.map { it.toDomainDiscordEmbed() } ?: emptyList(),
            webhookUrl = this.webhookUrl,
        )

    private fun SdkDiscordEmbed.toDomainDiscordEmbed(): DiscordEmbed =
        DiscordEmbed(
            title = this.title,
            type = this.type,
            description = this.description,
            url = this.url,
            timestamp = this.timestamp,
            color = this.color,
            footer = this.footer?.toDomainFooterEmbed(),
            image = this.image?.toDomainImageEmbed(),
            thumbnail = this.thumbnail?.toDomainThumbnailEmbed(),
            video = this.video?.toDomainVideoEmbed(),
            provider = this.provider?.toDomainProviderEmbed(),
            author = this.author?.toDomainAuthorEmbed(),
            fields = this.fields?.map { it.toDomainFieldEmbed() },
        )

    private fun SdkFooterEmbed.toDomainFooterEmbed(): FooterEmbed =
        FooterEmbed(
            text = this.text,
            iconUrl = this.iconUrl,
            proxyIconUrl = this.proxyIconUrl,
        )

    private fun SdkImageEmbed.toDomainImageEmbed(): ImageEmbed =
        ImageEmbed(
            url = this.url,
            proxyUrl = this.proxyUrl,
            height = this.height,
            width = this.width,
        )

    private fun SdkThumbnailEmbed.toDomainThumbnailEmbed(): ThumbnailEmbed =
        ThumbnailEmbed(
            url = this.url,
            proxyUrl = this.proxyUrl,
            height = this.height,
            width = this.width,
        )

    private fun SdkVideoEmbed.toDomainVideoEmbed(): VideoEmbed =
        VideoEmbed(
            url = this.url,
            height = this.height,
            width = this.width,
        )

    private fun SdkProviderEmbed.toDomainProviderEmbed(): ProviderEmbed =
        ProviderEmbed(
            name = this.name,
            url = this.url,
        )

    private fun SdkAuthorEmbed.toDomainAuthorEmbed(): AuthorEmbed =
        AuthorEmbed(
            name = this.name,
            url = this.url,
            iconUrl = this.iconUrl,
            proxyIconUrl = this.proxyIconUrl,
        )

    private fun SdkFieldEmbed.toDomainFieldEmbed(): FieldEmbed =
        FieldEmbed(
            name = this.name,
            value = this.value,
            inline = this.inline,
        )

    private fun SdkTelegramMessage.toDomainTelegramNotificationData(): ScriptTelegramNotification =
        ScriptTelegramNotification(
            chatId = this.chatId,
            text = this.text,
            parseMode = this.parseMode?.toDomainTelegramParseMode(),
            disableWebPagePreview = this.disableWebPagePreview,
            disableNotification = this.disableNotification,
            replyToMessageId = this.replyToMessageId,
            botToken = this.botToken,
        )

    private fun SdkTelegramParseMode.toDomainTelegramParseMode(): TelegramParseMode =
        when (this) {
            SdkTelegramParseMode.MARKDOWN -> TelegramParseMode.MARKDOWN
            SdkTelegramParseMode.MARKDOWN_V2 -> TelegramParseMode.MARKDOWN_V2
            SdkTelegramParseMode.HTML -> TelegramParseMode.HTML
        }

    private fun SdkEmailMessage.toDomainEmailNotificationData(): ScriptEmailNotification =
        ScriptEmailNotification(
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
}

package com.cereal.client.infrastructure.data.notification.discord.mapper

import com.cereal.client.infrastructure.data.notification.discord.serializable.SerializableAuthorEmbed
import com.cereal.client.infrastructure.data.notification.discord.serializable.SerializableDiscordEmbed
import com.cereal.client.infrastructure.data.notification.discord.serializable.SerializableDiscordMessage
import com.cereal.client.infrastructure.data.notification.discord.serializable.SerializableFieldEmbed
import com.cereal.client.infrastructure.data.notification.discord.serializable.SerializableFooterEmbed
import com.cereal.client.infrastructure.data.notification.discord.serializable.SerializableImageEmbed
import com.cereal.client.infrastructure.data.notification.discord.serializable.SerializableProviderEmbed
import com.cereal.client.infrastructure.data.notification.discord.serializable.SerializableThumbnailEmbed
import com.cereal.client.infrastructure.data.notification.discord.serializable.SerializableVideoEmbed
import com.cereal.sdk.component.notification.discord.model.DiscordEmbed
import com.cereal.sdk.component.notification.discord.model.DiscordMessage
import com.cereal.sdk.component.notification.discord.model.embed.AuthorEmbed
import com.cereal.sdk.component.notification.discord.model.embed.FieldEmbed
import com.cereal.sdk.component.notification.discord.model.embed.FooterEmbed
import com.cereal.sdk.component.notification.discord.model.embed.ImageEmbed
import com.cereal.sdk.component.notification.discord.model.embed.ProviderEmbed
import com.cereal.sdk.component.notification.discord.model.embed.ThumbnailEmbed
import com.cereal.sdk.component.notification.discord.model.embed.VideoEmbed

/**
 * Mapper to convert SDK Discord models to serializable models for JSON serialization.
 */
object DiscordModelMapper {
    fun toSerializable(message: DiscordMessage): SerializableDiscordMessage =
        SerializableDiscordMessage(
            username = message.username,
            content = message.content,
            avatarUrl = message.avatarUrl,
            tts = message.tts,
            embeds = message.embeds?.map { toSerializable(it) },
            webhookUrl = message.webhookUrl,
        )

    private fun toSerializable(embed: DiscordEmbed): SerializableDiscordEmbed =
        SerializableDiscordEmbed(
            title = embed.title,
            type = embed.type,
            description = embed.description,
            url = embed.url,
            timestamp = embed.timestamp,
            color = embed.color,
            footer = embed.footer?.let { toSerializable(it) },
            image = embed.image?.let { toSerializable(it) },
            thumbnail = embed.thumbnail?.let { toSerializable(it) },
            video = embed.video?.let { toSerializable(it) },
            provider = embed.provider?.let { toSerializable(it) },
            author = embed.author?.let { toSerializable(it) },
            fields = embed.fields?.map { toSerializable(it) },
        )

    private fun toSerializable(author: AuthorEmbed): SerializableAuthorEmbed =
        SerializableAuthorEmbed(
            name = author.name,
            url = author.url,
            iconUrl = author.iconUrl,
            proxyIconUrl = author.proxyIconUrl,
        )

    private fun toSerializable(field: FieldEmbed): SerializableFieldEmbed =
        SerializableFieldEmbed(
            name = field.name,
            value = field.value,
            inline = field.inline,
        )

    private fun toSerializable(footer: FooterEmbed): SerializableFooterEmbed =
        SerializableFooterEmbed(
            text = footer.text,
            iconUrl = footer.iconUrl,
            proxyIconUrl = footer.proxyIconUrl,
        )

    private fun toSerializable(image: ImageEmbed): SerializableImageEmbed =
        SerializableImageEmbed(
            url = image.url,
            proxyUrl = image.proxyUrl,
            height = image.height,
            width = image.width,
        )

    private fun toSerializable(provider: ProviderEmbed): SerializableProviderEmbed =
        SerializableProviderEmbed(
            name = provider.name,
            url = provider.url,
        )

    private fun toSerializable(thumbnail: ThumbnailEmbed): SerializableThumbnailEmbed =
        SerializableThumbnailEmbed(
            url = thumbnail.url,
            proxyUrl = thumbnail.proxyUrl,
            height = thumbnail.height,
            width = thumbnail.width,
        )

    private fun toSerializable(video: VideoEmbed): SerializableVideoEmbed =
        SerializableVideoEmbed(
            url = video.url,
            height = video.height,
            width = video.width,
        )
}

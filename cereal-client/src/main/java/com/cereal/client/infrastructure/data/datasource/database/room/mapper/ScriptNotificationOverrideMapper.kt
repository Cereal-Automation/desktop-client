package com.cereal.client.infrastructure.data.datasource.database.room.mapper

import com.cereal.client.domain.model.notification.DiscordOverrides
import com.cereal.client.domain.model.notification.EmailOverrides
import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.domain.model.notification.TelegramOverrides
import com.cereal.client.infrastructure.data.datasource.database.room.entity.ScriptNotificationOverrideEntity
import com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Mapper for converting between ScriptNotificationOverrides domain model and ScriptNotificationOverrideEntity.
 */
class ScriptNotificationOverrideMapper {
    /**
     * Converts a domain model to a database entity.
     */
    @OptIn(ExperimentalTime::class)
    fun toEntity(
        packageId: String,
        overrides: ScriptNotificationOverrides,
    ): ScriptNotificationOverrideEntity {
        val now = Clock.System.now()
        return ScriptNotificationOverrideEntity(
            id = UUID.randomUUID(),
            packageId = UUID.fromString(packageId),
            discordWebhookUrl = overrides.discordOverrides?.webhookUrl?.let { EncryptedString(it) },
            telegramBotToken = overrides.telegramOverrides?.botToken?.let { EncryptedString(it) },
            telegramChatId = overrides.telegramOverrides?.chatId?.let { EncryptedString(it) },
            emailSmtpHost = overrides.emailOverrides?.smtpHost?.let { EncryptedString(it) },
            emailSmtpPort = overrides.emailOverrides?.smtpPort,
            emailUsername = overrides.emailOverrides?.username?.let { EncryptedString(it) },
            emailPassword = overrides.emailOverrides?.password?.let { EncryptedString(it) },
            emailFrom = overrides.emailOverrides?.from?.let { EncryptedString(it) },
            emailTo = overrides.emailOverrides?.to?.let { EncryptedString(it) },
            emailUseTls = overrides.emailOverrides?.useTls,
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * Converts a database entity to a domain model.
     * Returns null if the entity has no overrides configured.
     */
    fun toDomain(entity: ScriptNotificationOverrideEntity?): ScriptNotificationOverrides? {
        if (entity == null) return null

        val discordOverrides =
            entity.discordWebhookUrl?.value?.takeIf { it.isNotBlank() }?.let {
                DiscordOverrides(webhookUrl = it)
            }

        val telegramOverrides =
            if (
                !entity.telegramBotToken?.value.isNullOrBlank() &&
                !entity.telegramChatId?.value.isNullOrBlank()
            ) {
                TelegramOverrides(
                    botToken = entity.telegramBotToken.value,
                    chatId = entity.telegramChatId.value,
                )
            } else {
                null
            }

        val emailSmtpPort = entity.emailSmtpPort
        val hasSmtpHost = !entity.emailSmtpHost?.value.isNullOrBlank()
        val hasUsername = !entity.emailUsername?.value.isNullOrBlank()
        val hasPassword = !entity.emailPassword?.value.isNullOrBlank()
        val hasFrom = !entity.emailFrom?.value.isNullOrBlank()
        val hasTo = !entity.emailTo?.value.isNullOrBlank()
        val hasEmailCredentials = hasSmtpHost && hasUsername && hasPassword
        val hasEmailAddresses = hasFrom && hasTo
        val emailOverrides =
            // emailSmtpPort != null is kept inline so it smart-casts the nullable port below.
            if (hasEmailCredentials && hasEmailAddresses && emailSmtpPort != null) {
                EmailOverrides(
                    smtpHost = entity.emailSmtpHost.value,
                    smtpPort = emailSmtpPort,
                    username = entity.emailUsername.value,
                    password = entity.emailPassword.value,
                    from = entity.emailFrom.value,
                    to = entity.emailTo.value,
                    useTls = entity.emailUseTls ?: true,
                )
            } else {
                null
            }

        // Return null if no overrides are configured
        if (discordOverrides == null && telegramOverrides == null && emailOverrides == null) {
            return null
        }

        return ScriptNotificationOverrides(
            discordOverrides = discordOverrides,
            telegramOverrides = telegramOverrides,
            emailOverrides = emailOverrides,
        )
    }
}

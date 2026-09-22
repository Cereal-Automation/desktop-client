package com.cereal.client.infrastructure.data.datasource.discord

import com.cereal.client.domain.repository.ApplicationPreferenceRepository
import com.cereal.client.domain.repository.NotificationSettingsRepository
import kotlinx.coroutines.flow.Flow

class DiscordSettings(
    private val applicationPreferenceRepository: ApplicationPreferenceRepository,
    private val notificationSettingsRepository: NotificationSettingsRepository,
) {
    suspend fun getDiscordActivityStatusEnabled(): Flow<Boolean> = applicationPreferenceRepository.isDiscordActivityStatusEnabled()

    suspend fun getDiscordWebhookUrl(): Flow<String> = notificationSettingsRepository.getDiscordWebhookUrl()
}

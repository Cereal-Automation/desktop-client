package com.cereal.client.application.interactor.settings.discord

import com.cereal.client.application.Interactor
import com.cereal.client.domain.provider.DiscordProvider
import com.cereal.client.domain.repository.ApplicationPreferenceRepository

class SetDiscordActivityStatusEnabledInteractor(
    private val applicationPreferenceRepository: ApplicationPreferenceRepository,
    private val discordRepository: DiscordProvider,
) : Interactor<Unit, SetDiscordActivityStatusEnabledInteractor.Params>() {
    override suspend fun run(params: Params) {
        applicationPreferenceRepository.setDiscordActivityStatusEnabled(params.enabled)
        when (params.enabled) {
            true -> discordRepository.restorePresence()
            false -> discordRepository.clearPresence()
        }
    }

    data class Params(
        val enabled: Boolean,
    )
}

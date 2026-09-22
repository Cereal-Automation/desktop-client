package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.discord.rpc.DiscordPresence
import com.cereal.client.domain.provider.DiscordProvider
import com.cereal.client.infrastructure.data.datasource.discord.DiscordRpcDataSource
import com.cereal.client.infrastructure.data.datasource.discord.DiscordSettings
import kotlinx.coroutines.flow.first

class DiscordProviderImpl(
    private val discordSettings: DiscordSettings,
    private val discordRpcDataSource: DiscordRpcDataSource,
) : DiscordProvider {
    override suspend fun initialize() {
        discordRpcDataSource.initialize()
    }

    override suspend fun disconnect() {
        discordRpcDataSource.close()
    }

    override suspend fun updatePresence(discordPresence: DiscordPresence) {
        if (discordSettings.getDiscordActivityStatusEnabled().first()) {
            discordRpcDataSource.updatePresence(discordPresence)
        }
    }

    /**
     * Called only when the user enables Discord activity status, so the guard is always true and not needed.
     */
    override suspend fun restorePresence() {
        discordRpcDataSource.restorePresence()
    }

    /**
     * Called only when the user disables Discord activity status — must always clear presence regardless of the setting value.
     */
    override suspend fun clearPresence() {
        discordRpcDataSource.clearPresence()
    }
}

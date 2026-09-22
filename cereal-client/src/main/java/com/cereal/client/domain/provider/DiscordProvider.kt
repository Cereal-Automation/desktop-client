package com.cereal.client.domain.provider

import com.cereal.client.domain.model.discord.rpc.DiscordPresence

interface DiscordProvider {
    suspend fun initialize()

    suspend fun disconnect()

    suspend fun updatePresence(discordPresence: DiscordPresence)

    suspend fun restorePresence()

    suspend fun clearPresence()
}

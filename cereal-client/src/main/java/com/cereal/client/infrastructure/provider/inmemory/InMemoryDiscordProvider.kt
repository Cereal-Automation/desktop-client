package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.model.discord.rpc.DiscordPresence
import com.cereal.client.domain.provider.DiscordProvider

/**
 * In-memory Discord repository used in the `mock` flavor to avoid connecting to the Discord
 * RPC daemon during local development. Records presence transitions so interactor tests can assert
 * on real state instead of verifying mock calls.
 */
class InMemoryDiscordProvider : DiscordProvider {
    var restorePresenceCount = 0
        private set
    var clearPresenceCount = 0
        private set

    override suspend fun initialize() {
        // No-op in fake implementation.
    }

    override suspend fun disconnect() {
        // No-op in fake implementation.
    }

    override suspend fun updatePresence(discordPresence: DiscordPresence) {
        // No-op in fake implementation.
    }

    override suspend fun restorePresence() {
        restorePresenceCount++
    }

    override suspend fun clearPresence() {
        clearPresenceCount++
    }
}

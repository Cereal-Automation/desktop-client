@file:OptIn(ExperimentalTime::class)

package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.discord.rpc.DiscordPresence
import com.cereal.client.infrastructure.data.datasource.discord.DiscordRpcDataSource
import com.cereal.client.infrastructure.data.datasource.discord.DiscordSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DiscordProviderImplTest {
    private lateinit var discordSettings: DiscordSettings
    private lateinit var discordRpcDataSource: DiscordRpcDataSource
    private lateinit var repository: DiscordProviderImpl
    private lateinit var presence: DiscordPresence

    @BeforeEach
    fun setUp() {
        discordSettings = mockk(relaxed = true)
        discordRpcDataSource = mockk(relaxed = true)
        repository = DiscordProviderImpl(discordSettings, discordRpcDataSource)
        presence =
            DiscordPresence(
                state = "state",
                details = "details",
                startTimestamp = Instant.fromEpochSeconds(0),
                endTimestamp = Instant.fromEpochSeconds(3600),
                largeImageKey = "large_image",
                largeImageText = null,
                smallImageKey = null,
                smallImageText = null,
                partyId = null,
                partySize = 1,
                partyMax = 4,
                matchSecret = null,
                joinSecret = null,
                spectateSecret = null,
                instance = false,
            )
    }

    @Test
    fun `restorePresence should delegate to discordRpcDataSource`() =
        runTest {
            repository.restorePresence()

            coVerify(exactly = 1) { discordRpcDataSource.restorePresence() }
        }

    @Test
    fun `clearPresence should delegate to discordRpcDataSource`() =
        runTest {
            repository.clearPresence()

            coVerify(exactly = 1) { discordRpcDataSource.clearPresence() }
        }

    @Test
    fun `updatePresence should call discordRpcDataSource when activity status is enabled`() =
        runTest {
            coEvery { discordSettings.getDiscordActivityStatusEnabled() } returns flowOf(true)

            repository.updatePresence(presence)

            coVerify(exactly = 1) { discordRpcDataSource.updatePresence(presence) }
        }

    @Test
    fun `updatePresence should not call discordRpcDataSource when activity status is disabled`() =
        runTest {
            coEvery { discordSettings.getDiscordActivityStatusEnabled() } returns flowOf(false)

            repository.updatePresence(presence)

            coVerify(exactly = 0) { discordRpcDataSource.updatePresence(any()) }
        }
}

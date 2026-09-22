package com.cereal.client.application.interactor.settings.discord

import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryDiscordProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SetDiscordActivityStatusEnabledInteractorTest {
    private lateinit var applicationPreferenceRepository: InMemoryApplicationPreferenceRepository
    private lateinit var discordRepository: InMemoryDiscordProvider
    private lateinit var interactor: SetDiscordActivityStatusEnabledInteractor

    @BeforeEach
    fun setUp() {
        applicationPreferenceRepository = InMemoryApplicationPreferenceRepository()
        discordRepository = InMemoryDiscordProvider()
        interactor = SetDiscordActivityStatusEnabledInteractor(applicationPreferenceRepository, discordRepository)
    }

    @Test
    fun `run should save preference and restore Discord presence when enabled is true`() =
        runTest {
            interactor.run(SetDiscordActivityStatusEnabledInteractor.Params(enabled = true))

            assertEquals(true, applicationPreferenceRepository.isDiscordActivityStatusEnabled().first())
            assertEquals(1, discordRepository.restorePresenceCount)
            assertEquals(0, discordRepository.clearPresenceCount)
        }

    @Test
    fun `run should save preference and clear Discord presence when enabled is false`() =
        runTest {
            interactor.run(SetDiscordActivityStatusEnabledInteractor.Params(enabled = false))

            assertEquals(false, applicationPreferenceRepository.isDiscordActivityStatusEnabled().first())
            assertEquals(1, discordRepository.clearPresenceCount)
            assertEquals(0, discordRepository.restorePresenceCount)
        }
}

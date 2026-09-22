package com.cereal.client.application.interactor.settings.developers

import com.cereal.client.application.interactor.exception.ScriptSyncException
import com.cereal.client.application.script.ScriptSyncManager
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SetDevelopmentScriptsInteractorTest {
    private lateinit var applicationPreferenceRepository: InMemoryApplicationPreferenceRepository

    // Concrete orchestration collaborator with no in-memory fake; mocked to drive the sync outcome.
    private lateinit var scriptSyncManager: ScriptSyncManager
    private lateinit var interactor: SetDevelopmentScriptsInteractor

    @BeforeEach
    fun setUp() {
        applicationPreferenceRepository = InMemoryApplicationPreferenceRepository()
        scriptSyncManager = mockk()
        interactor = SetDevelopmentScriptsInteractor(applicationPreferenceRepository, scriptSyncManager)
    }

    @Test
    fun `run enables development scripts and persists value when sync succeeds`() =
        runTest {
            coEvery { scriptSyncManager.sync(updateScripts = true) } returns emptyMap()

            interactor.run(SetDevelopmentScriptsInteractor.Params(enabled = true))

            assertEquals(true, applicationPreferenceRepository.isDevelopmentScriptsEnabled().first())
            coVerify(exactly = 1) { scriptSyncManager.sync(updateScripts = true) }
        }

    @Test
    fun `run does nothing when the value already matches the requested value`() =
        runTest {
            // Default is false; requesting false must not trigger a sync.
            interactor.run(SetDevelopmentScriptsInteractor.Params(enabled = false))

            assertEquals(false, applicationPreferenceRepository.isDevelopmentScriptsEnabled().first())
            coVerify(exactly = 0) { scriptSyncManager.sync(any()) }
        }

    @Test
    fun `run rolls back the persisted value and throws when sync fails`() =
        runTest {
            coEvery { scriptSyncManager.sync(updateScripts = true) } returns
                mapOf("com.example.one" to IllegalStateException("boom"))

            val thrown =
                assertThrows<ScriptSyncException> {
                    interactor.run(SetDevelopmentScriptsInteractor.Params(enabled = true))
                }

            assertEquals(setOf("com.example.one"), thrown.exceptions.keys)
            // The interactor reverts the optimistic write on failure.
            assertEquals(false, applicationPreferenceRepository.isDevelopmentScriptsEnabled().first())
        }
}

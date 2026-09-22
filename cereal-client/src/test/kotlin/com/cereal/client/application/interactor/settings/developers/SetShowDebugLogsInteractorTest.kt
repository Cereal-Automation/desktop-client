package com.cereal.client.application.interactor.settings.developers

import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SetShowDebugLogsInteractorTest {
    private lateinit var applicationPreferenceRepository: InMemoryApplicationPreferenceRepository
    private lateinit var interactor: SetShowDebugLogsInteractor

    @BeforeEach
    fun setUp() {
        applicationPreferenceRepository = InMemoryApplicationPreferenceRepository()
        interactor = SetShowDebugLogsInteractor(applicationPreferenceRepository)
    }

    @Test
    fun `run should persist enabled true when params enabled is true`() =
        runTest {
            interactor.run(SetShowDebugLogsInteractor.Params(enabled = true))

            assertEquals(true, applicationPreferenceRepository.isShowDebugLogs().first())
        }

    @Test
    fun `run should persist enabled false when params enabled is false`() =
        runTest {
            // Flip it on first so persisting false is an observable change, not just the default.
            interactor.run(SetShowDebugLogsInteractor.Params(enabled = true))

            interactor.run(SetShowDebugLogsInteractor.Params(enabled = false))

            assertEquals(false, applicationPreferenceRepository.isShowDebugLogs().first())
        }
}

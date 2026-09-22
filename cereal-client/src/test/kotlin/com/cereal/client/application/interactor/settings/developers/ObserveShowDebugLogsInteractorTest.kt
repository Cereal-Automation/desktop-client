package com.cereal.client.application.interactor.settings.developers

import com.cereal.client.application.Interactor
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(FlowPreview::class)
class ObserveShowDebugLogsInteractorTest {
    private lateinit var applicationPreferenceRepository: InMemoryApplicationPreferenceRepository
    private lateinit var interactor: ObserveShowDebugLogsInteractor

    @BeforeEach
    fun setUp() {
        applicationPreferenceRepository = InMemoryApplicationPreferenceRepository()
        interactor = ObserveShowDebugLogsInteractor(applicationPreferenceRepository)
    }

    @Test
    fun `run emits the default false value`() =
        runTest {
            val result = interactor.run(Interactor.None()).first()

            assertEquals(false, result)
        }

    @Test
    fun `run emits true after the preference is enabled`() =
        runTest {
            applicationPreferenceRepository.setShowDebugLogs(true)

            val result = interactor.run(Interactor.None()).first()

            assertEquals(true, result)
        }
}

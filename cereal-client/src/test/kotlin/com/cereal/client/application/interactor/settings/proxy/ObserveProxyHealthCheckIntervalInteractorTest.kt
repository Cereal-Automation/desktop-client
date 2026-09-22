package com.cereal.client.application.interactor.settings.proxy

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.settings.ProxyHealthCheckInterval
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(FlowPreview::class)
class ObserveProxyHealthCheckIntervalInteractorTest {
    private lateinit var applicationPreferenceRepository: InMemoryApplicationPreferenceRepository
    private lateinit var interactor: ObserveProxyHealthCheckIntervalInteractor

    @BeforeEach
    fun setUp() {
        applicationPreferenceRepository = InMemoryApplicationPreferenceRepository()
        interactor = ObserveProxyHealthCheckIntervalInteractor(applicationPreferenceRepository)
    }

    @Test
    fun `run emits the default interval`() =
        runTest {
            val result = interactor.run(Interactor.None()).first()

            assertEquals(ProxyHealthCheckInterval.Default, result)
        }

    @Test
    fun `run emits the updated interval after it is changed`() =
        runTest {
            applicationPreferenceRepository.setProxyHealthCheckInterval(ProxyHealthCheckInterval.EVERY_24_HOURS)

            val result = interactor.run(Interactor.None()).first()

            assertEquals(ProxyHealthCheckInterval.EVERY_24_HOURS, result)
        }
}

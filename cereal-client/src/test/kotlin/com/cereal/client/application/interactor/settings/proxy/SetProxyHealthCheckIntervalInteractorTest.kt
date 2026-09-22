package com.cereal.client.application.interactor.settings.proxy

import com.cereal.client.domain.model.settings.ProxyHealthCheckInterval
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SetProxyHealthCheckIntervalInteractorTest {
    private lateinit var applicationPreferenceRepository: InMemoryApplicationPreferenceRepository
    private lateinit var interactor: SetProxyHealthCheckIntervalInteractor

    @BeforeEach
    fun setUp() {
        applicationPreferenceRepository = InMemoryApplicationPreferenceRepository()
        interactor = SetProxyHealthCheckIntervalInteractor(applicationPreferenceRepository)
    }

    @Test
    fun `run persists the given interval`() =
        runTest {
            interactor.run(SetProxyHealthCheckIntervalInteractor.Params(ProxyHealthCheckInterval.EVERY_24_HOURS))

            assertEquals(
                ProxyHealthCheckInterval.EVERY_24_HOURS,
                applicationPreferenceRepository.getProxyHealthCheckInterval().first(),
            )
        }

    @Test
    fun `run persists OFF when interval is OFF`() =
        runTest {
            interactor.run(SetProxyHealthCheckIntervalInteractor.Params(ProxyHealthCheckInterval.OFF))

            assertEquals(
                ProxyHealthCheckInterval.OFF,
                applicationPreferenceRepository.getProxyHealthCheckInterval().first(),
            )
        }
}

package com.cereal.client.application.interactor.proxy

import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealth
import com.cereal.client.domain.model.proxy.ProxyHealthStatus
import com.cereal.client.domain.repository.ProxyRepository
import com.cereal.client.domain.service.ProxyHealthChecker
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class CheckProxyHealthInteractorTest {
    private fun proxy(address: String = "127.0.0.1") =
        Proxy(
            id = UUID.randomUUID(),
            address = address,
            port = 8080,
            username = null,
            password = null,
        )

    @Test
    fun `run should return the checker result and persist it on the proxy`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val healthChecker = mockk<ProxyHealthChecker>()
            val interactor = CheckProxyHealthInteractor(healthChecker, proxyRepository)

            val group = ProxyGroup("group-1", "Group", 0, emptySequence())
            proxyRepository.createProxyGroup(group)
            val target = proxy()
            proxyRepository.createOrUpdateProxy(target, group)

            val healthy = ProxyHealth.healthy(Instant.fromEpochMilliseconds(1000), 42)
            coEvery { healthChecker.check(target) } returns healthy

            val result = interactor.run(CheckProxyHealthInteractor.Params(target))

            assertEquals(healthy, result)
            val stored = proxyRepository.getProxiesFromGroup(group.id).single { it.id == target.id }
            assertEquals(ProxyHealthStatus.HEALTHY, stored.health.status)
            assertEquals(42L, stored.health.latencyMs)
        }

    @Test
    fun `run should persist a failed health status`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val healthChecker = mockk<ProxyHealthChecker>()
            val interactor = CheckProxyHealthInteractor(healthChecker, proxyRepository)

            val group = ProxyGroup("group-1", "Group", 0, emptySequence())
            proxyRepository.createProxyGroup(group)
            val target = proxy()
            proxyRepository.createOrUpdateProxy(target, group)

            val failed = ProxyHealth.failed(Instant.fromEpochMilliseconds(2000), "timeout")
            coEvery { healthChecker.check(target) } returns failed

            val result = interactor.run(CheckProxyHealthInteractor.Params(target))

            assertEquals(failed, result)
            val stored = proxyRepository.getProxiesFromGroup(group.id).single { it.id == target.id }
            assertEquals(ProxyHealthStatus.FAILED, stored.health.status)
            assertEquals("timeout", stored.health.lastError)
        }

    @Test
    fun `run should propagate exception when the health checker fails`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val healthChecker = mockk<ProxyHealthChecker>()
            val interactor = CheckProxyHealthInteractor(healthChecker, proxyRepository)

            val group = ProxyGroup("group-1", "Group", 0, emptySequence())
            proxyRepository.createProxyGroup(group)
            val target = proxy()
            proxyRepository.createOrUpdateProxy(target, group)

            coEvery { healthChecker.check(target) } throws RuntimeException("network error")

            assertThrows<RuntimeException> {
                interactor.run(CheckProxyHealthInteractor.Params(target))
            }

            // Health was never persisted because the checker failed first.
            val stored = proxyRepository.getProxiesFromGroup(group.id).single { it.id == target.id }
            assertEquals(ProxyHealthStatus.UNKNOWN, stored.health.status)
        }

    @Test
    fun `run should propagate exception when persisting health fails`() =
        runTest {
            val proxyRepository = mockk<ProxyRepository>()
            val healthChecker = mockk<ProxyHealthChecker>()
            val interactor = CheckProxyHealthInteractor(healthChecker, proxyRepository)
            val target = proxy()

            coEvery { healthChecker.check(target) } returns ProxyHealth.healthy(Instant.fromEpochMilliseconds(0), 1)
            coEvery { proxyRepository.updateProxyHealth(target.id, any()) } throws RuntimeException("Database error")

            assertThrows<RuntimeException> {
                interactor.run(CheckProxyHealthInteractor.Params(target))
            }
        }
}

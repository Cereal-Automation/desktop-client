package com.cereal.client.application.interactor.proxy

import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealth
import com.cereal.client.domain.model.proxy.ProxyHealthStatus
import com.cereal.client.domain.service.ProxyHealthChecker
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class CheckProxiesInGroupInteractorTest {
    private fun proxy(address: String) =
        Proxy(
            id = UUID.randomUUID(),
            address = address,
            port = 8080,
            username = null,
            password = null,
        )

    /** Maps each proxy to a deterministic health so the test can assert on outcomes, not call order. */
    private fun checkerReturning(map: Map<UUID, ProxyHealth>): ProxyHealthChecker {
        val checker = mockk<ProxyHealthChecker>()
        coEvery { checker.check(any()) } answers {
            val p = firstArg<Proxy>()
            map.getValue(p.id)
        }
        return checker
    }

    @Test
    fun `run should emit a result per proxy and persist each health`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val group = ProxyGroup("group-1", "Group", 0, emptySequence())
            proxyRepository.createProxyGroup(group)
            val a = proxy("10.0.0.1")
            val b = proxy("10.0.0.2")
            proxyRepository.createOrUpdateProxy(a, group)
            proxyRepository.createOrUpdateProxy(b, group)

            val healthA = ProxyHealth.healthy(Instant.fromEpochMilliseconds(1), 10)
            val healthB = ProxyHealth.failed(Instant.fromEpochMilliseconds(2), "down")
            val checker = checkerReturning(mapOf(a.id to healthA, b.id to healthB))
            val interactor = CheckProxiesInGroupInteractor(proxyRepository, checker)

            val results = interactor.run(CheckProxiesInGroupInteractor.Params(group)).toList()

            // Concurrency means ordering is not guaranteed; assert on the set of outcomes.
            assertEquals(
                mapOf(a.id to healthA, b.id to healthB),
                results.associate { it.proxyId to it.health },
            )
            val stored = proxyRepository.getProxiesFromGroup(group.id).associateBy { it.id }
            assertEquals(ProxyHealthStatus.HEALTHY, stored.getValue(a.id).health.status)
            assertEquals(ProxyHealthStatus.FAILED, stored.getValue(b.id).health.status)
        }

    @Test
    fun `run should emit nothing for an empty group`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val group = ProxyGroup("group-1", "Group", 0, emptySequence())
            proxyRepository.createProxyGroup(group)
            val checker = mockk<ProxyHealthChecker>()
            val interactor = CheckProxiesInGroupInteractor(proxyRepository, checker)

            val results = interactor.run(CheckProxiesInGroupInteractor.Params(group)).toList()

            assertTrue(results.isEmpty())
        }

    @Test
    fun `run should respect a custom max concurrency and still process every proxy`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val group = ProxyGroup("group-1", "Group", 0, emptySequence())
            proxyRepository.createProxyGroup(group)
            val proxies = (1..5).map { proxy("10.0.0.$it") }
            proxies.forEach { proxyRepository.createOrUpdateProxy(it, group) }
            val healthById = proxies.associate { it.id to ProxyHealth.healthy(Instant.fromEpochMilliseconds(0), 1) }
            val checker = checkerReturning(healthById)
            val interactor = CheckProxiesInGroupInteractor(proxyRepository, checker)

            val results =
                interactor
                    .run(
                        CheckProxiesInGroupInteractor.Params(group, maxConcurrent = 1),
                    ).toList()

            assertEquals(proxies.map { it.id }.toSet(), results.map { it.proxyId }.toSet())
        }

    @Test
    fun `run should propagate exception when the health checker fails`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val group = ProxyGroup("group-1", "Group", 0, emptySequence())
            proxyRepository.createProxyGroup(group)
            proxyRepository.createOrUpdateProxy(proxy("10.0.0.1"), group)
            val checker = mockk<ProxyHealthChecker>()
            coEvery { checker.check(any()) } throws RuntimeException("network error")
            val interactor = CheckProxiesInGroupInteractor(proxyRepository, checker)

            assertThrows<RuntimeException> {
                interactor.run(CheckProxiesInGroupInteractor.Params(group)).toList()
            }
        }
}

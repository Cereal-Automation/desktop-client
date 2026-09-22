package com.cereal.client.application.interactor.proxy

import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealth
import com.cereal.client.domain.model.proxy.ProxyHealthStatus
import com.cereal.client.domain.repository.ProxyRepository
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
class DeleteFailedProxiesInGroupInteractorTest {
    private fun proxy(
        address: String,
        status: ProxyHealthStatus,
    ): Proxy {
        val health =
            when (status) {
                ProxyHealthStatus.FAILED -> ProxyHealth.failed(Instant.fromEpochMilliseconds(0), "boom")
                ProxyHealthStatus.HEALTHY -> ProxyHealth.healthy(Instant.fromEpochMilliseconds(0), 12)
                ProxyHealthStatus.UNKNOWN -> ProxyHealth.Unknown
            }
        return Proxy(
            id = UUID.randomUUID(),
            address = address,
            port = 8080,
            username = null,
            password = null,
            health = health,
        )
    }

    @Test
    fun `run should delete only failed proxies and return their count`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = DeleteFailedProxiesInGroupInteractor(proxyRepository)

            val group = ProxyGroup("group-1", "Group", 0, emptySequence())
            proxyRepository.createProxyGroup(group)
            val failedA = proxy("10.0.0.1", ProxyHealthStatus.FAILED)
            val failedB = proxy("10.0.0.2", ProxyHealthStatus.FAILED)
            val healthy = proxy("10.0.0.3", ProxyHealthStatus.HEALTHY)
            val unknown = proxy("10.0.0.4", ProxyHealthStatus.UNKNOWN)
            proxyRepository.createOrUpdateProxy(failedA, group)
            proxyRepository.createOrUpdateProxy(failedB, group)
            proxyRepository.createOrUpdateProxy(healthy, group)
            proxyRepository.createOrUpdateProxy(unknown, group)

            val deleted = interactor.run(DeleteFailedProxiesInGroupInteractor.Params(group))

            assertEquals(2, deleted)
            val remaining = proxyRepository.getProxiesFromGroup(group.id).map { it.id }.toSet()
            assertEquals(setOf(healthy.id, unknown.id), remaining)
        }

    @Test
    fun `run should return zero when there are no failed proxies`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = DeleteFailedProxiesInGroupInteractor(proxyRepository)

            val group = ProxyGroup("group-1", "Group", 0, emptySequence())
            proxyRepository.createProxyGroup(group)
            val healthy = proxy("10.0.0.1", ProxyHealthStatus.HEALTHY)
            proxyRepository.createOrUpdateProxy(healthy, group)

            val deleted = interactor.run(DeleteFailedProxiesInGroupInteractor.Params(group))

            assertEquals(0, deleted)
            assertEquals(listOf(healthy.id), proxyRepository.getProxiesFromGroup(group.id).map { it.id })
        }

    @Test
    fun `run should return zero for an unknown group`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = DeleteFailedProxiesInGroupInteractor(proxyRepository)

            val deleted =
                interactor.run(
                    DeleteFailedProxiesInGroupInteractor.Params(
                        ProxyGroup("missing", "Missing", 0, emptySequence()),
                    ),
                )

            assertEquals(0, deleted)
        }

    @Test
    fun `run should propagate exception when repository fails`() =
        runTest {
            val proxyRepository = mockk<ProxyRepository>()
            val interactor = DeleteFailedProxiesInGroupInteractor(proxyRepository)
            val group = ProxyGroup("group-1", "Group", 0, emptySequence())

            coEvery { proxyRepository.deleteFailedProxiesFromGroup(group) } throws RuntimeException("Database error")

            assertThrows<RuntimeException> {
                interactor.run(DeleteFailedProxiesInGroupInteractor.Params(group))
            }
        }
}

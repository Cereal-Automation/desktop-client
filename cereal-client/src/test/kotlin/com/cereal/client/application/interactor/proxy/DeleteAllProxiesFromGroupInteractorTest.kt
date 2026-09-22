package com.cereal.client.application.interactor.proxy

import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.repository.ProxyRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class DeleteAllProxiesFromGroupInteractorTest {
    private fun proxy(address: String) =
        Proxy(
            id = UUID.randomUUID(),
            address = address,
            port = 8080,
            username = null,
            password = null,
        )

    @Test
    fun `run should remove all proxies from the targeted group`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = DeleteAllProxiesFromGroupInteractor(proxyRepository)

            val group = ProxyGroup("group-1", "Group", 0, emptySequence())
            proxyRepository.createProxyGroup(group)
            proxyRepository.createOrUpdateProxy(proxy("10.0.0.1"), group)
            proxyRepository.createOrUpdateProxy(proxy("10.0.0.2"), group)

            interactor.run(DeleteAllProxiesFromGroupInteractor.Params(group))

            assertTrue(proxyRepository.getProxiesFromGroup(group.id).isEmpty())
        }

    @Test
    fun `run should not affect proxies in other groups`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = DeleteAllProxiesFromGroupInteractor(proxyRepository)

            val target = ProxyGroup("group-1", "Target", 0, emptySequence())
            val other = ProxyGroup("group-2", "Other", 0, emptySequence())
            proxyRepository.createProxyGroup(target)
            proxyRepository.createProxyGroup(other)
            proxyRepository.createOrUpdateProxy(proxy("10.0.0.1"), target)
            val keptProxy = proxy("10.0.0.2")
            proxyRepository.createOrUpdateProxy(keptProxy, other)

            interactor.run(DeleteAllProxiesFromGroupInteractor.Params(target))

            assertTrue(proxyRepository.getProxiesFromGroup(target.id).isEmpty())
            assertEquals(listOf(keptProxy.id), proxyRepository.getProxiesFromGroup(other.id).map { it.id })
        }

    @Test
    fun `run should be a no-op when group has no proxies`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = DeleteAllProxiesFromGroupInteractor(proxyRepository)

            val group = ProxyGroup("group-1", "Group", 0, emptySequence())
            proxyRepository.createProxyGroup(group)

            interactor.run(DeleteAllProxiesFromGroupInteractor.Params(group))

            assertTrue(proxyRepository.getProxiesFromGroup(group.id).isEmpty())
        }

    @Test
    fun `run should propagate exception when repository fails`() =
        runTest {
            val proxyRepository = mockk<ProxyRepository>()
            val interactor = DeleteAllProxiesFromGroupInteractor(proxyRepository)
            val group = ProxyGroup("group-1", "Group", 0, emptySequence())

            coEvery { proxyRepository.deleteProxiesFromGroup(group) } throws RuntimeException("Database error")

            assertThrows<RuntimeException> {
                interactor.run(DeleteAllProxiesFromGroupInteractor.Params(group))
            }
        }
}

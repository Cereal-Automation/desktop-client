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

class DeleteProxyInteractorTest {
    private fun proxy(address: String = "127.0.0.1") =
        Proxy(
            id = UUID.randomUUID(),
            address = address,
            port = 8080,
            username = null,
            password = null,
        )

    @Test
    fun `run should remove the targeted proxy from its group`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = DeleteProxyInteractor(proxyRepository)

            val group = ProxyGroup("group-1", "Group", 0, emptySequence())
            proxyRepository.createProxyGroup(group)
            val toDelete = proxy("10.0.0.1")
            val toKeep = proxy("10.0.0.2")
            proxyRepository.createOrUpdateProxy(toDelete, group)
            proxyRepository.createOrUpdateProxy(toKeep, group)

            interactor.run(DeleteProxyInteractor.Params(toDelete))

            val remaining = proxyRepository.getProxiesFromGroup(group.id)
            assertEquals(listOf(toKeep.id), remaining.map { it.id })
        }

    @Test
    fun `run should be a no-op when proxy is not present`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = DeleteProxyInteractor(proxyRepository)

            val group = ProxyGroup("group-1", "Group", 0, emptySequence())
            proxyRepository.createProxyGroup(group)
            val existing = proxy("10.0.0.1")
            proxyRepository.createOrUpdateProxy(existing, group)

            interactor.run(DeleteProxyInteractor.Params(proxy("10.0.0.99")))

            val remaining = proxyRepository.getProxiesFromGroup(group.id)
            assertEquals(listOf(existing.id), remaining.map { it.id })
        }

    @Test
    fun `run should leave the group empty when its only proxy is deleted`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = DeleteProxyInteractor(proxyRepository)

            val group = ProxyGroup("group-1", "Group", 0, emptySequence())
            proxyRepository.createProxyGroup(group)
            val only = proxy("10.0.0.1")
            proxyRepository.createOrUpdateProxy(only, group)

            interactor.run(DeleteProxyInteractor.Params(only))

            assertTrue(proxyRepository.getProxiesFromGroup(group.id).isEmpty())
        }

    @Test
    fun `run should propagate exception when repository fails`() =
        runTest {
            val proxyRepository = mockk<ProxyRepository>()
            val interactor = DeleteProxyInteractor(proxyRepository)
            val target = proxy()

            coEvery { proxyRepository.deleteProxy(target) } throws RuntimeException("Database error")

            assertThrows<RuntimeException> {
                interactor.run(DeleteProxyInteractor.Params(target))
            }
        }
}

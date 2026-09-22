package com.cereal.client.application.interactor.proxy

import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class GetProxiesInteractorTest {
    private fun proxy(address: String) =
        Proxy(
            id = UUID.randomUUID(),
            address = address,
            port = 8080,
            username = null,
            password = null,
        )

    @Test
    fun `run should emit the proxies currently in the group`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val group = ProxyGroup("group-1", "Group", 0, emptySequence())
            proxyRepository.createProxyGroup(group)
            val a = proxy("10.0.0.1")
            val b = proxy("10.0.0.2")
            proxyRepository.createOrUpdateProxy(a, group)
            proxyRepository.createOrUpdateProxy(b, group)

            val interactor = GetProxiesInteractor(proxyRepository)

            val result = interactor.run(GetProxiesInteractor.Params(group)).first()

            assertEquals(listOf(a.id, b.id), result.map { it.id })
        }

    @Test
    fun `run should emit an empty list for a group with no proxies`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val group = ProxyGroup("group-1", "Group", 0, emptySequence())
            proxyRepository.createProxyGroup(group)
            val interactor = GetProxiesInteractor(proxyRepository)

            val result = interactor.run(GetProxiesInteractor.Params(group)).first()

            assertTrue(result.isEmpty())
        }

    @Test
    fun `run should reflect a proxy added after the flow was obtained`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val group = ProxyGroup("group-1", "Group", 0, emptySequence())
            proxyRepository.createProxyGroup(group)
            val interactor = GetProxiesInteractor(proxyRepository)

            val added = proxy("10.0.0.1")
            proxyRepository.createOrUpdateProxy(added, group)

            val result = interactor.run(GetProxiesInteractor.Params(group)).first()

            assertEquals(listOf(added.id), result.map { it.id })
        }
}

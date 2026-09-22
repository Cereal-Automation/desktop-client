package com.cereal.client.application.interactor.proxy

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetProxyGroupsInteractorTest {
    @Test
    fun `should return proxy groups from repository`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = GetProxyGroupsInteractor(proxyRepository)

            proxyRepository.createProxyGroup(ProxyGroup("group-1", "Group One", 0, emptySequence()))
            proxyRepository.createProxyGroup(ProxyGroup("group-2", "Group Two", 0, emptySequence()))

            val result = interactor.run(Interactor.None()).first()

            assertEquals(listOf("group-1", "group-2"), result.map { it.id })
            assertEquals(listOf("Group One", "Group Two"), result.map { it.name })
        }

    @Test
    fun `should return empty list when no proxy groups exist`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = GetProxyGroupsInteractor(proxyRepository)

            val result = interactor.run(Interactor.None()).first()

            assertEquals(emptyList<ProxyGroup>(), result)
        }
}

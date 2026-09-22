package com.cereal.client.application.interactor.proxy

import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.repository.ProxyRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CreateProxyGroupInteractorTest {
    @Test
    fun `run should create proxy group and persist it in repository`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = CreateProxyGroupInteractor(proxyRepository)

            val created = interactor.run(CreateProxyGroupInteractor.Params(name = "My Group"))

            val stored = proxyRepository.getProxyGroups().first()
            assertEquals(listOf(created.id), stored.map { it.id })
            assertEquals(listOf("My Group"), stored.map { it.name })
        }

    @Test
    fun `run should return a group with a generated id and empty items`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = CreateProxyGroupInteractor(proxyRepository)

            val created = interactor.run(CreateProxyGroupInteractor.Params(name = "Fresh"))

            assertNotNull(created.id)
            assertTrue(created.id.isNotBlank())
            assertEquals("Fresh", created.name)
            assertEquals(0, created.numberOfItems)
            assertEquals(emptyList<Any>(), created.items.toList())
        }

    @Test
    fun `run should create multiple groups with distinct ids`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = CreateProxyGroupInteractor(proxyRepository)

            val first = interactor.run(CreateProxyGroupInteractor.Params(name = "One"))
            val second = interactor.run(CreateProxyGroupInteractor.Params(name = "Two"))

            assertTrue(first.id != second.id)
            val stored = proxyRepository.getProxyGroups().first()
            assertEquals(listOf("One", "Two"), stored.map { it.name })
        }

    @Test
    fun `run should propagate exception when repository fails`() =
        runTest {
            val proxyRepository = mockk<ProxyRepository>()
            val interactor = CreateProxyGroupInteractor(proxyRepository)

            coEvery { proxyRepository.createProxyGroup(any()) } throws RuntimeException("Database error")

            assertThrows<RuntimeException> {
                interactor.run(CreateProxyGroupInteractor.Params(name = "Boom"))
            }
        }
}

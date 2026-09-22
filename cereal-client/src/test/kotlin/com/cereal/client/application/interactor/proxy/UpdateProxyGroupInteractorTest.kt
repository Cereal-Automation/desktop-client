package com.cereal.client.application.interactor.proxy

import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.repository.ProxyRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UpdateProxyGroupInteractorTest {
    @Test
    fun `run should rename the targeted proxy group`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = UpdateProxyGroupInteractor(proxyRepository)

            proxyRepository.createProxyGroup(ProxyGroup("group-1", "Old Name", 0, emptySequence()))

            interactor.run(UpdateProxyGroupInteractor.Params(groupId = "group-1", name = "New Name"))

            val stored = proxyRepository.getProxyGroups().first()
            assertEquals(listOf("New Name"), stored.map { it.name })
        }

    @Test
    fun `run should only rename the matching group and leave others untouched`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = UpdateProxyGroupInteractor(proxyRepository)

            proxyRepository.createProxyGroup(ProxyGroup("group-1", "First", 0, emptySequence()))
            proxyRepository.createProxyGroup(ProxyGroup("group-2", "Second", 0, emptySequence()))

            interactor.run(UpdateProxyGroupInteractor.Params(groupId = "group-2", name = "Renamed"))

            val stored = proxyRepository.getProxyGroups().first()
            assertEquals(listOf("First", "Renamed"), stored.map { it.name })
        }

    @Test
    fun `run should be a no-op when group id does not exist`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = UpdateProxyGroupInteractor(proxyRepository)

            proxyRepository.createProxyGroup(ProxyGroup("group-1", "First", 0, emptySequence()))

            interactor.run(UpdateProxyGroupInteractor.Params(groupId = "missing", name = "Nope"))

            val stored = proxyRepository.getProxyGroups().first()
            assertEquals(listOf("First"), stored.map { it.name })
        }

    @Test
    fun `run should propagate exception when repository fails`() =
        runTest {
            val proxyRepository = mockk<ProxyRepository>()
            val interactor = UpdateProxyGroupInteractor(proxyRepository)

            coEvery { proxyRepository.updateProxyGroup("group-1", "New Name") } throws RuntimeException("Database error")

            assertThrows<RuntimeException> {
                interactor.run(UpdateProxyGroupInteractor.Params(groupId = "group-1", name = "New Name"))
            }
        }
}

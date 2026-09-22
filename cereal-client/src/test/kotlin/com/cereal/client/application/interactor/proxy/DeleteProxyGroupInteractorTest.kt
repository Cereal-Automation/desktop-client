package com.cereal.client.application.interactor.proxy

import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.repository.ProxyRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.InvalidParameterException

class DeleteProxyGroupInteractorTest {
    @Test
    fun `run should delete proxy group when valid params provided`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = DeleteProxyGroupInteractor(proxyRepository)

            val proxyGroup = ProxyGroup("group-1", "Test Group", 0, emptySequence())
            proxyRepository.createProxyGroup(proxyGroup)
            assertEquals(listOf(proxyGroup.id), proxyRepository.getProxyGroups().first().map { it.id })

            interactor.run(DeleteProxyGroupInteractor.Params(proxyGroup))

            assertTrue(proxyRepository.getProxyGroups().first().isEmpty())
        }

    @Test
    fun `run should throw InvalidParameterException when proxyGroup is null`() =
        runTest {
            val proxyRepository = InMemoryProxyRepository()
            val interactor = DeleteProxyGroupInteractor(proxyRepository)

            assertThrows<InvalidParameterException> {
                interactor.run(DeleteProxyGroupInteractor.Params(null))
            }

            // The empty repository is untouched.
            assertTrue(proxyRepository.getProxyGroups().first().isEmpty())
        }

    @Test
    fun `run should propagate exception when repository fails`() =
        runTest {
            // Failure injection has no observable state to back it, so the repository edge is mocked here.
            val proxyRepository = mockk<ProxyRepository>()
            val interactor = DeleteProxyGroupInteractor(proxyRepository)

            val proxyGroup = ProxyGroup("group-1", "Test Group", 0, emptySequence())
            coEvery { proxyRepository.deleteProxyGroup(proxyGroup) } throws RuntimeException("Database error")

            assertThrows<RuntimeException> {
                interactor.run(DeleteProxyGroupInteractor.Params(proxyGroup))
            }
        }
}

package com.cereal.client.application.interactor.proxy.provider

import com.cereal.client.domain.model.exception.InvalidProxyProviderTokenException
import com.cereal.client.domain.model.exception.NoProxyProviderSubUserException
import com.cereal.client.domain.model.exception.ProxyProviderConnectivityException
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyProviderConnectorRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryProxyConnectionProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Interactor-level tests against the in-memory fake connection provider + connector repository,
 * exercising connect (incl. sub-user selection and the zero-sub-user path), disconnect, and
 * get-connected. The connection provider and connector repository share state, mirroring production.
 */
class ProxyProviderInteractorsTest {
    private val provider = ProxyVendor.MARSPROXIES
    private val validToken = InMemoryProxyConnectionProvider.DEMO_TOKEN

    @Test
    fun `connect resolves the highest-traffic subuser hash`() =
        runTest {
            val interactor = ConnectProxyProviderInteractor(InMemoryProxyConnectionProvider(InMemoryProxyProviderConnectorRepository()))

            val connector = interactor.run(ConnectProxyProviderInteractor.Params(provider, validToken))

            // DEMO_SUBUSERS: hash_low(12), hash_top(60), hash_mid(30) → hash_top has the most traffic.
            assertEquals("hash_top", connector.subUserHash)
        }

    @Test
    fun `connect rejects an invalid token`() =
        runTest {
            val interactor = ConnectProxyProviderInteractor(InMemoryProxyConnectionProvider(InMemoryProxyProviderConnectorRepository()))

            assertThrows<InvalidProxyProviderTokenException> {
                interactor.run(ConnectProxyProviderInteractor.Params(provider, "wrong-token"))
            }
        }

    @Test
    fun `connect surfaces the zero-subuser path`() =
        runTest {
            val interactor =
                ConnectProxyProviderInteractor(
                    InMemoryProxyConnectionProvider(InMemoryProxyProviderConnectorRepository(), subUsers = emptyList()),
                )

            assertThrows<NoProxyProviderSubUserException> {
                interactor.run(ConnectProxyProviderInteractor.Params(provider, validToken))
            }
        }

    @Test
    fun `connect surfaces connectivity failures`() =
        runTest {
            val interactor =
                ConnectProxyProviderInteractor(
                    InMemoryProxyConnectionProvider(InMemoryProxyProviderConnectorRepository(), connectivityFails = true),
                )

            assertThrows<ProxyProviderConnectivityException> {
                interactor.run(ConnectProxyProviderInteractor.Params(provider, validToken))
            }
        }

    @Test
    fun `disconnect clears the connector and getConnected then emits null`() =
        runTest {
            val connectorRepository = InMemoryProxyProviderConnectorRepository()
            val connectionProvider = InMemoryProxyConnectionProvider(connectorRepository)
            ConnectProxyProviderInteractor(connectionProvider).run(ConnectProxyProviderInteractor.Params(provider, validToken))

            assertNotNull(GetConnectedProxyProviderInteractor(connectorRepository).run(GetConnectedProxyProviderInteractor.Params(provider)).first())

            DisconnectProxyProviderInteractor(connectionProvider).run(DisconnectProxyProviderInteractor.Params(provider))

            assertNull(GetConnectedProxyProviderInteractor(connectorRepository).run(GetConnectedProxyProviderInteractor.Params(provider)).first())
        }
}

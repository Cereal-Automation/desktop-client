package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.proxy.ProxyProviderConnector
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.ProxyProviderConnectorDataSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ProxyProviderConnectorRepositoryImplTest {
    private val user = User(id = "u1", name = "n", email = "e", encryptionKey = "k", accessToken = "t")
    private val userSession = mockk<UserSession> { coEvery { requireUser() } returns user }
    private val dataSource = FakeConnectorDataSource()
    private val repository = ProxyProviderConnectorRepositoryImpl(dataSource, userSession)

    @Test
    fun `upsert then read round-trips the connector for the provider`() =
        runTest {
            repository.upsertConnector(connector(subUserHash = "top"))

            val read = repository.getConnectedProvider(ProxyVendor.MARSPROXIES).first()
            assertEquals("top", read?.subUserHash)
        }

    @Test
    fun `delete clears the connector`() =
        runTest {
            repository.upsertConnector(connector(subUserHash = "top"))

            repository.deleteConnector(ProxyVendor.MARSPROXIES)

            assertNull(repository.getConnectedProvider(ProxyVendor.MARSPROXIES).first())
        }

    private fun connector(subUserHash: String) =
        ProxyProviderConnector(
            provider = ProxyVendor.MARSPROXIES,
            connectedAt =
                kotlin.time.Clock.System
                    .now(),
            lastSyncAt =
                kotlin.time.Clock.System
                    .now(),
            subUserHash = subUserHash,
            availableTrafficGb = 10.0,
            subUserCount = 1,
            credentialKey = "k",
        )

    private class FakeConnectorDataSource : ProxyProviderConnectorDataSource {
        private val flow = MutableStateFlow<ProxyProviderConnector?>(null)

        override fun observeConnector(
            user: User,
            provider: ProxyVendor,
        ): Flow<ProxyProviderConnector?> = flow.map { it?.takeIf { c -> c.provider == provider } }

        override suspend fun upsertConnector(
            user: User,
            connector: ProxyProviderConnector,
        ) {
            flow.value = connector
        }

        override suspend fun deleteConnector(
            user: User,
            provider: ProxyVendor,
        ) {
            if (flow.value?.provider == provider) flow.value = null
        }
    }
}

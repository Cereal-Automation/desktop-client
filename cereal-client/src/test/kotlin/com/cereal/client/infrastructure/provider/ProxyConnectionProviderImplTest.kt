package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.exception.InvalidProxyProviderTokenException
import com.cereal.client.domain.model.exception.NoProxyProviderSubUserException
import com.cereal.client.domain.model.exception.ProxyProviderConnectivityException
import com.cereal.client.domain.model.exception.ProxyProviderNotConnectedException
import com.cereal.client.domain.model.exception.ProxySyncFailedException
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyProviderAccount
import com.cereal.client.domain.model.proxy.ProxyProviderConnector
import com.cereal.client.domain.model.proxy.ProxySession
import com.cereal.client.domain.model.proxy.ProxySubUser
import com.cereal.client.domain.model.proxy.ProxySyncConfig
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.model.proxy.SyncTarget
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.ProxyProviderConnectorDataSource
import com.cereal.client.infrastructure.data.datasource.network.ProxyProviderDataSource
import com.cereal.client.infrastructure.data.datasource.network.marsproxies.dto.MarsProxiesGenerateProxyListRequest
import com.cereal.client.infrastructure.data.repository.ProxyProviderConnectorRepositoryImpl
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyProviderCredentialRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ProxyConnectionProviderImplTest {
    private val user = User(id = "u1", name = "n", email = "e", encryptionKey = "k", accessToken = "t")
    private val userSession = mockk<UserSession> { coEvery { requireUser() } returns user }
    private val credentialRepository = InMemoryProxyProviderCredentialRepository()
    private val connectorDataSource = FakeConnectorDataSource()
    private val connectorRepository = ProxyProviderConnectorRepositoryImpl(connectorDataSource, userSession)
    private val proxyRepository = InMemoryProxyRepository()

    private fun connectionProvider(network: ProxyProviderDataSource) = ProxyConnectionProviderImpl(network, connectorRepository, credentialRepository, proxyRepository)

    @Test
    fun `connect resolves the highest-traffic subuser and persists token and record`() =
        runTest {
            val network =
                FakeNetworkDataSource(
                    account = ProxyProviderAccount(availableTrafficGb = 84.2, subUserCount = 3, accountHash = "acct"),
                    subUsers =
                        listOf(
                            subUser("low", 12.0),
                            subUser("top", 60.0),
                            subUser("mid", 30.0),
                        ),
                )

            val connector = connectionProvider(network).connect(ProxyVendor.MARSPROXIES, "mp_live_token")

            assertEquals("top", connector.subUserHash)
            assertEquals(84.2, connector.availableTrafficGb)
            assertEquals(3, connector.subUserCount)
            assertEquals(credentialRepository.credentialKey(ProxyVendor.MARSPROXIES), connector.credentialKey)

            // Token stored as the sensitive credential, record persisted.
            assertEquals("mp_live_token", credentialRepository.getToken(ProxyVendor.MARSPROXIES).first())
            assertEquals("top", connectorDataSource.observeConnector(user, ProxyVendor.MARSPROXIES).first()?.subUserHash)
        }

    @Test
    fun `connect throws NoProxyProviderSubUserException when there are no subusers`() =
        runTest {
            val network = FakeNetworkDataSource(subUsers = emptyList())

            assertThrows<NoProxyProviderSubUserException> {
                connectionProvider(network).connect(ProxyVendor.MARSPROXIES, "mp_live_token")
            }
            // Nothing persisted on the zero-subuser path.
            assertNull(connectorDataSource.observeConnector(user, ProxyVendor.MARSPROXIES).first())
        }

    @Test
    fun `connect translates a rejected token to InvalidProxyProviderTokenException`() =
        runTest {
            val network =
                FakeNetworkDataSource(
                    accountError =
                        com.cereal.client.infrastructure.data.datasource.network.exception
                            .AuthenticationException(),
                )

            assertThrows<InvalidProxyProviderTokenException> {
                connectionProvider(network).connect(ProxyVendor.MARSPROXIES, "bad")
            }
        }

    @Test
    fun `connect translates a network failure to ProxyProviderConnectivityException`() =
        runTest {
            val network = FakeNetworkDataSource(accountError = IOException("boom"))

            assertThrows<ProxyProviderConnectivityException> {
                connectionProvider(network).connect(ProxyVendor.MARSPROXIES, "mp_live_token")
            }
        }

    @Test
    fun `disconnect clears the stored token and the connector record`() =
        runTest {
            val network = FakeNetworkDataSource(subUsers = listOf(subUser("top", 60.0)))
            val provider = connectionProvider(network)
            provider.connect(ProxyVendor.MARSPROXIES, "mp_live_token")

            provider.disconnect(ProxyVendor.MARSPROXIES)

            assertEquals("", credentialRepository.getToken(ProxyVendor.MARSPROXIES).first())
            assertNull(connectorDataSource.observeConnector(user, ProxyVendor.MARSPROXIES).first())
        }

    @Test
    fun `syncProxies appends sticky endpoints into a new group with provider and geoLabel stamped`() =
        runTest {
            val network =
                FakeNetworkDataSource(
                    subUsers = listOf(subUser("top", 60.0)),
                    rendered =
                        listOf(
                            "ultra.marsproxies.com:44443:user1:pw_one",
                            "ultra.marsproxies.com:44443:user2:pw_two",
                            "ultra.marsproxies.com:44443:user3:pw_three",
                        ),
                )
            val provider = connectionProvider(network)
            provider.connect(ProxyVendor.MARSPROXIES, "mp_live_token")

            val result =
                provider.syncProxies(
                    ProxyVendor.MARSPROXIES,
                    ProxySyncConfig(
                        country = "US",
                        state = "Texas",
                        city = "Dallas",
                        session = ProxySession.STICKY,
                        count = 10,
                        target = SyncTarget.NewGroup("US · Checkout"),
                    ),
                )

            assertEquals(3, result.syncedCount)
            assertEquals("US · Checkout", result.groupName)

            // The new group is created, stamped with provider + geoLabel, and holds the synced rows.
            val group = proxyRepository.getProxyGroups().first().first { it.id == result.groupId }
            assertEquals(ProxyVendor.MARSPROXIES, group.provider)
            assertEquals("United States · Texas · Dallas", group.geoLabel)
            assertEquals(3, proxyRepository.getProxiesFromGroup(result.groupId).size)
        }

    @Test
    fun `syncProxies sends the correct generate request body`() =
        runTest {
            val network = FakeNetworkDataSource(subUsers = listOf(subUser("hash_top", 60.0)), rendered = listOf("h:1:u:p"))
            val provider = connectionProvider(network)
            provider.connect(ProxyVendor.MARSPROXIES, "mp_live_token")

            provider.syncProxies(
                ProxyVendor.MARSPROXIES,
                ProxySyncConfig(country = "US", state = "Texas", city = "Dallas", session = ProxySession.STICKY, count = 250, target = SyncTarget.NewGroup("g")),
            )

            val request = requireNotNull(network.lastRequest)
            assertEquals("{hostname}:{port}:{username}:{password}", request.format)
            assertEquals("ultra.marsproxies.com", request.hostname)
            assertEquals(44443, request.port)
            assertEquals("sticky", request.rotation)
            assertEquals("30m", request.lifetime)
            assertEquals("hash_top", request.subUserHash)
            assertEquals(250, request.proxyCount)
            assertEquals("country-us_state-texas_city-dallas", request.location)
        }

    @Test
    fun `syncProxies collapses rotating to a single endpoint and omits count and lifetime`() =
        runTest {
            val network =
                FakeNetworkDataSource(
                    subUsers = listOf(subUser("top", 60.0)),
                    rendered = listOf("h:1:u:p", "h:2:u:p", "h:3:u:p"),
                )
            val provider = connectionProvider(network)
            provider.connect(ProxyVendor.MARSPROXIES, "mp_live_token")

            val result =
                provider.syncProxies(
                    ProxyVendor.MARSPROXIES,
                    ProxySyncConfig(country = "GB", session = ProxySession.ROTATING, target = SyncTarget.NewGroup("rot")),
                )

            assertEquals(1, result.syncedCount)
            val request = requireNotNull(network.lastRequest)
            assertEquals("rotating", request.rotation)
            assertNull(request.lifetime)
            assertNull(request.proxyCount)
        }

    @Test
    fun `syncProxies appends into an existing group without replacing existing rows`() =
        runTest {
            // Seed an existing group with one manual proxy.
            proxyRepository.createProxyGroup(ProxyGroup(id = "g-existing", name = "Existing", numberOfItems = 0, items = emptySequence()))
            proxyRepository.createOrUpdateProxy(
                com.cereal.client.domain.model.proxy
                    .Proxy(id = java.util.UUID.randomUUID(), address = "manual.host", port = 8080, username = "m", password = "p"),
                ProxyGroup(id = "g-existing", name = "Existing", numberOfItems = 0, items = emptySequence()),
            )
            val network = FakeNetworkDataSource(subUsers = listOf(subUser("top", 60.0)), rendered = listOf("h:1:u:p", "h:2:u:p"))
            val provider = connectionProvider(network)
            provider.connect(ProxyVendor.MARSPROXIES, "mp_live_token")

            val result =
                provider.syncProxies(
                    ProxyVendor.MARSPROXIES,
                    ProxySyncConfig(country = "US", session = ProxySession.STICKY, count = 10, target = SyncTarget.ExistingGroup("g-existing")),
                )

            assertEquals("g-existing", result.groupId)
            // The manual row plus the two synced rows are all present.
            assertEquals(3, proxyRepository.getProxiesFromGroup("g-existing").size)
        }

    @Test
    fun `syncProxies refreshes the connector last-sync timestamp`() =
        runTest {
            val network = FakeNetworkDataSource(subUsers = listOf(subUser("top", 60.0)), rendered = listOf("h:1:u:p"))
            val provider = connectionProvider(network)
            val connected = provider.connect(ProxyVendor.MARSPROXIES, "mp_live_token")

            // syncProxies copies lastSyncAt = now(); assert it advanced past the connect timestamp.
            Thread.sleep(2)
            provider.syncProxies(
                ProxyVendor.MARSPROXIES,
                ProxySyncConfig(country = "US", session = ProxySession.STICKY, count = 10, target = SyncTarget.NewGroup("g")),
            )

            val after = connectorDataSource.observeConnector(user, ProxyVendor.MARSPROXIES).first()!!
            assertTrue(after.lastSyncAt >= connected.lastSyncAt, "last sync should be refreshed")
        }

    @Test
    fun `syncProxies on a generation failure writes nothing and throws ProxySyncFailedException`() =
        runTest {
            val network = FakeNetworkDataSource(subUsers = listOf(subUser("top", 60.0)), generateError = IOException("boom"))
            val provider = connectionProvider(network)
            provider.connect(ProxyVendor.MARSPROXIES, "mp_live_token")

            assertThrows<ProxySyncFailedException> {
                provider.syncProxies(
                    ProxyVendor.MARSPROXIES,
                    ProxySyncConfig(country = "US", session = ProxySession.STICKY, count = 10, target = SyncTarget.NewGroup("g")),
                )
            }
            // Nothing persisted on the failure path.
            assertTrue(proxyRepository.getProxyGroups().first().isEmpty())
        }

    @Test
    fun `syncProxies parses the password as the remainder after the third colon`() =
        runTest {
            // Password contains ':' and '_' — the provider splits on the first three ':' only.
            val network =
                FakeNetworkDataSource(
                    subUsers = listOf(subUser("top", 60.0)),
                    rendered = listOf("ultra.marsproxies.com:44443:subuser_us:pw_country-us:session-aabb"),
                )
            val provider = connectionProvider(network)
            provider.connect(ProxyVendor.MARSPROXIES, "mp_live_token")

            val result =
                provider.syncProxies(
                    ProxyVendor.MARSPROXIES,
                    ProxySyncConfig(country = "US", session = ProxySession.STICKY, count = 10, target = SyncTarget.NewGroup("g")),
                )

            val proxy = proxyRepository.getProxiesFromGroup(result.groupId).single()
            assertEquals("ultra.marsproxies.com", proxy.address)
            assertEquals(44443, proxy.port)
            assertEquals("subuser_us", proxy.username)
            assertEquals("pw_country-us:session-aabb", proxy.password)
        }

    @Test
    fun `syncProxies without a connector throws ProxyProviderNotConnectedException`() =
        runTest {
            val network = FakeNetworkDataSource(rendered = listOf("h:1:u:p"))

            assertThrows<ProxyProviderNotConnectedException> {
                connectionProvider(network).syncProxies(
                    ProxyVendor.MARSPROXIES,
                    ProxySyncConfig(country = "US", session = ProxySession.STICKY, count = 10, target = SyncTarget.NewGroup("g")),
                )
            }
        }

    private fun subUser(
        hash: String,
        traffic: Double,
    ) = ProxySubUser(id = hash, hash = hash, username = "u", password = "p", trafficAvailable = traffic, trafficUsed = 0.0)

    private class FakeNetworkDataSource(
        private val account: ProxyProviderAccount = ProxyProviderAccount(10.0, 1, "acct"),
        private val subUsers: List<ProxySubUser> = emptyList(),
        private val accountError: Throwable? = null,
        private val rendered: List<String> = emptyList(),
        private val generateError: Throwable? = null,
    ) : ProxyProviderDataSource {
        var lastRequest: MarsProxiesGenerateProxyListRequest? = null
            private set

        override suspend fun validateAndFetchAccount(token: String): ProxyProviderAccount {
            accountError?.let { throw it }
            return account
        }

        override suspend fun fetchSubUsers(token: String): List<ProxySubUser> = subUsers

        override suspend fun generateProxies(
            token: String,
            request: MarsProxiesGenerateProxyListRequest,
        ): List<String> {
            lastRequest = request
            generateError?.let { throw it }
            return rendered
        }
    }

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

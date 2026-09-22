package com.cereal.client.infrastructure.data.repository

import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealth
import com.cereal.client.domain.model.user.User
import com.cereal.client.fixtures.InMemoryProxyDataSource
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemProxyTemplateDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.models.ProxyTxt
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ProxyRepositoryImplTest {
    private lateinit var repository: ProxyRepositoryImpl

    private val proxyDataSource = InMemoryProxyDataSource()
    private val userSession = mockk<UserSession>(relaxed = true)
    private val fileSystemProxyTemplateDataSource = mockk<FileSystemProxyTemplateDataSource>(relaxed = true)

    private val user =
        User(
            id = "user-1",
            name = "Test User",
            email = "test@example.com",
            encryptionKey = "key",
            accessToken = "token",
        )

    @BeforeEach
    fun setUp() {
        // MockK: UserSession is a precondition (auth state), not the seam under test.
        coEvery { userSession.requireUser() } returns user
        repository =
            ProxyRepositoryImpl(
                proxyDataSource = proxyDataSource,
                userSession = userSession,
                fileSystemProxyTemplateDataSource = fileSystemProxyTemplateDataSource,
            )
    }

    private fun createProxyGroup(id: String = "group-1"): ProxyGroup =
        ProxyGroup(
            id = id,
            name = "Group $id",
            numberOfItems = 0,
            items = emptySequence(),
        )

    private fun createProxy(
        id: UUID = UUID.randomUUID(),
        health: ProxyHealth = ProxyHealth.Unknown,
    ): Proxy =
        Proxy(
            id = id,
            address = "127.0.0.1",
            port = 8080,
            username = "user",
            password = "pass",
            health = health,
        )

    @Test
    fun `createProxyGroup makes the group observable via getProxyGroups`() =
        runTest {
            val group = createProxyGroup()

            repository.createProxyGroup(group)

            val groups = repository.getProxyGroups().first()
            assertEquals(listOf(group.id), groups.map { it.id })
            assertEquals("Group group-1", groups.single().name)
        }

    @Test
    fun `updateProxyGroup changes the stored group name`() =
        runTest {
            val group = createProxyGroup()
            repository.createProxyGroup(group)

            repository.updateProxyGroup(group.id, "New Name")

            assertEquals(
                "New Name",
                repository
                    .getProxyGroups()
                    .first()
                    .single()
                    .name,
            )
        }

    @Test
    fun `deleteProxyGroup removes the group`() =
        runTest {
            val group = createProxyGroup("group-9")
            repository.createProxyGroup(group)

            repository.deleteProxyGroup(group)

            assertTrue(repository.getProxyGroups().first().isEmpty())
        }

    @Test
    fun `createOrUpdateProxy makes the proxy observable via getProxiesFromGroup and flow`() =
        runTest {
            val group = createProxyGroup("group-3")
            repository.createProxyGroup(group)
            val proxy = createProxy()

            repository.createOrUpdateProxy(proxy, group)

            assertEquals(listOf(proxy), repository.getProxiesFromGroup(group.id))
            assertEquals(listOf(proxy), repository.getProxiesFlow(group).first())
        }

    @Test
    fun `createOrUpdateProxy updates an existing proxy in place`() =
        runTest {
            val group = createProxyGroup("group-3")
            repository.createProxyGroup(group)
            val proxy = createProxy()
            repository.createOrUpdateProxy(proxy, group)

            val updated = proxy.copy(address = "10.10.10.10")
            repository.createOrUpdateProxy(updated, group)

            val stored = repository.getProxiesFromGroup(group.id)
            assertEquals(1, stored.size)
            assertEquals("10.10.10.10", stored.single().address)
        }

    @Test
    fun `deleteProxy removes the proxy from the group`() =
        runTest {
            val group = createProxyGroup("group-4")
            repository.createProxyGroup(group)
            val proxy = createProxy()
            repository.createOrUpdateProxy(proxy, group)

            repository.deleteProxy(proxy)

            assertTrue(repository.getProxiesFromGroup(group.id).isEmpty())
        }

    @Test
    fun `getProxiesInGroupCount reflects the number of stored proxies`() =
        runTest {
            val group = createProxyGroup("group-1")
            repository.createProxyGroup(group)
            repository.createOrUpdateProxy(createProxy(), group)
            repository.createOrUpdateProxy(createProxy(), group)

            assertEquals(2L, repository.getProxiesInGroupCount(group.id))
        }

    @Test
    fun `deleteProxiesFromGroup empties the group`() =
        runTest {
            val group = createProxyGroup("group-5")
            repository.createProxyGroup(group)
            repository.createOrUpdateProxy(createProxy(), group)
            repository.createOrUpdateProxy(createProxy(), group)

            repository.deleteProxiesFromGroup(group)

            assertEquals(0L, repository.getProxiesInGroupCount(group.id))
            assertTrue(repository.getProxiesFromGroup(group.id).isEmpty())
        }

    @Test
    fun `deleteFailedProxiesFromGroup removes only failed proxies and returns the removed count`() =
        runTest {
            val group = createProxyGroup("group-6")
            repository.createProxyGroup(group)
            val healthy = createProxy()
            val failed =
                createProxy(
                    health = ProxyHealth.failed(Instant.fromEpochMilliseconds(1), "boom"),
                )
            repository.createOrUpdateProxy(healthy, group)
            repository.createOrUpdateProxy(failed, group)

            val removed = repository.deleteFailedProxiesFromGroup(group)

            assertEquals(1, removed)
            assertEquals(listOf(healthy.id), repository.getProxiesFromGroup(group.id).map { it.id })
        }

    @Test
    fun `readFromFile maps template proxies to Proxy with unique generated ids`() =
        runTest {
            val file = File("proxies.txt")
            val template =
                listOf(
                    ProxyTxt(address = "10.0.0.1", port = 1000, username = "u1", password = "p1"),
                    ProxyTxt(address = "10.0.0.2", port = 2000, username = null, password = null),
                )
            // MockK: filesystem edge — FileSystemProxyTemplateDataSource reads from disk.
            every { fileSystemProxyTemplateDataSource.readFromTemplate(file) } returns template

            val result = repository.readFromFile(file)

            assertEquals(2, result.size)
            assertEquals("10.0.0.1", result[0].address)
            assertEquals(1000, result[0].port)
            assertEquals("u1", result[0].username)
            assertEquals("p1", result[0].password)
            assertEquals("10.0.0.2", result[1].address)
            assertEquals(2000, result[1].port)
            assertNull(result[1].username)
            assertNull(result[1].password)
            assertTrue(result[0].id != result[1].id)
        }

    @Test
    fun `updateProxyHealth is reflected in the stored proxy`() =
        runTest {
            val group = createProxyGroup("group-1")
            repository.createProxyGroup(group)
            val proxy = createProxy()
            repository.createOrUpdateProxy(proxy, group)
            val health = ProxyHealth.healthy(Instant.fromEpochMilliseconds(5), latencyMs = 12)

            repository.updateProxyHealth(proxy.id, health)

            assertEquals(health, repository.getProxiesFromGroup(group.id).single().health)
        }

    @Test
    fun `getStaleProxies returns proxies never checked or checked before the cutoff`() =
        runTest {
            val group = createProxyGroup("group-1")
            repository.createProxyGroup(group)
            val cutoff = Instant.fromEpochMilliseconds(1000)
            // Never checked (lastCheckedAt == null) -> stale.
            val neverChecked = createProxy()
            // Checked before the cutoff -> stale.
            val old =
                createProxy(
                    health = ProxyHealth.healthy(Instant.fromEpochMilliseconds(500), latencyMs = 1),
                )
            // Checked after the cutoff -> fresh.
            val fresh =
                createProxy(
                    health = ProxyHealth.healthy(Instant.fromEpochMilliseconds(1500), latencyMs = 1),
                )
            repository.createOrUpdateProxy(neverChecked, group)
            repository.createOrUpdateProxy(old, group)
            repository.createOrUpdateProxy(fresh, group)

            val stale = repository.getStaleProxies(cutoff).map { it.id }.toSet()

            assertTrue(neverChecked.id in stale)
            assertTrue(old.id in stale)
            assertFalse(fresh.id in stale)
        }

    @Test
    fun `getProxyGroups starts empty for a fresh user`() =
        runTest {
            assertTrue(repository.getProxyGroups().first().isEmpty())
        }

    @Test
    fun `getProxiesFromGroup returns proxies seeded through the group`() =
        runTest {
            val proxy = createProxy()
            val group =
                ProxyGroup(
                    id = "seeded",
                    name = "Seeded",
                    numberOfItems = 1,
                    items = sequenceOf(proxy),
                )

            repository.createProxyGroup(group)

            val stored = repository.getProxiesFromGroup("seeded")
            assertEquals(1, stored.size)
            assertNotNull(stored.firstOrNull { it.id == proxy.id })
        }
}

package com.cereal.client.infrastructure.data.datasource.database.integration

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.DatabaseImplementation
import com.cereal.client.infrastructure.data.datasource.database.ProxyDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.DatabaseConnector
import com.cereal.client.infrastructure.data.datasource.database.room.RoomDatabases
import com.cereal.client.infrastructure.data.datasource.database.room.RoomProxyDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.UserRoomDatabase
import com.cereal.client.infrastructure.data.datasource.filesystem.security.Encryption
import com.cereal.client.infrastructure.data.datasource.filesystem.security.EncryptionKey
import com.cereal.client.infrastructure.di.UserScopeProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.koin.core.context.GlobalContext
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.core.scope.Scope
import org.koin.dsl.module
import java.io.File
import java.util.UUID

/**
 * Parameterized integration test for ProxyDataSource implementations.
 */
class ProxyDataSourceParameterizedIntegrationTest {
    private lateinit var proxyDataSource: ProxyDataSource
    private lateinit var roomDatabases: RoomDatabases
    private lateinit var testUser: User
    private lateinit var tempDir: File

    private val userScopeProvider = TestUserScopeProvider()

    class TestUserScopeProvider : UserScopeProvider {
        override var currentScope: Scope? = null
    }

    /**
     * Sets up the test for a specific database implementation.
     */
    @Suppress("LongMethod")
    private fun setupForImplementation(implementation: DatabaseImplementation) {
        // Create temporary directory for test database
        tempDir =
            File.createTempFile("test", "").apply {
                delete()
                mkdirs()
            }

        // Create mock ApplicationConfig
        val applicationConfig =
            mockk<ApplicationConfig> {
                every { databaseDirectory } returns tempDir
                every { databaseName } returns "test.db"
                every { databaseEncryptionKey } returns "12345678901234567890123456789012" // 32 chars
            }

        // Mock Encryption companion object
        val testKeyBytes = "12345678901234567890123456789012".toByteArray() // 32 bytes
        mockkObject(Encryption)
        every {
            Encryption.getEncryptionKey(
                any(),
                any(),
                any(),
            )
        } returns EncryptionKey(testKeyBytes, testKeyBytes)

        // Initialize database implementations based on test configuration
        when (implementation) {
            DatabaseImplementation.ROOM -> {
                // Create the real RoomDatabases instance first
                roomDatabases = RoomDatabases(applicationConfig)

                // Set up Koin with the real RoomDatabases instance for the UserEncryptedStringConverter
                val applicationEncryptionKey = "12345678901234567890123456789012" // 32 chars

                val koinApp =
                    startKoin {
                        modules(
                            module {
                                single { roomDatabases }
                                single(
                                    qualifier =
                                        org.koin.core.qualifier
                                            .named("applicationEncryptionKey"),
                                ) { applicationEncryptionKey }
                                single<UserScopeProvider> { userScopeProvider }

                                scope(
                                    org.koin.core.qualifier
                                        .named("test-user-id"),
                                ) {
                                    scoped<UserRoomDatabase> {
                                        DatabaseConnector.connectUser(tempDir, "test-user-id")
                                    }
                                    scoped(
                                        org.koin.core.qualifier
                                            .named("UserEncryptionKey"),
                                    ) {
                                        EncryptionKey(testKeyBytes, testKeyBytes)
                                    }
                                }
                                scope(
                                    org.koin.core.qualifier
                                        .named("test-user-2-id"),
                                ) {
                                    scoped<UserRoomDatabase> {
                                        DatabaseConnector.connectUser(tempDir, "test-user-2-id")
                                    }
                                    scoped(
                                        org.koin.core.qualifier
                                            .named("UserEncryptionKey"),
                                    ) {
                                        EncryptionKey(testKeyBytes, testKeyBytes)
                                    }
                                }
                            },
                        )
                    }

                val user1Scope =
                    koinApp.koin.createScope(
                        "test-user-id",
                        org.koin.core.qualifier
                            .named("test-user-id"),
                    )
                koinApp.koin.createScope(
                    "test-user-2-id",
                    org.koin.core.qualifier
                        .named("test-user-2-id"),
                )

                // Set default scope
                userScopeProvider.currentScope = user1Scope

                proxyDataSource = RoomProxyDataSource(roomDatabases)
            }
        }

        // Create test user
        testUser =
            User(
                id = "test-user-id",
                name = "Test User",
                email = "test@example.com",
                encryptionKey = "12345678901234567890123456789012", // 32 chars
                accessToken = "fake-access-token-for-testing",
            )
    }

    /**
     * Cleans up after a specific database implementation test.
     */
    private fun tearDownForImplementation(implementation: DatabaseImplementation) {
        // Clean up database connections based on implementation
        when (implementation) {
            DatabaseImplementation.ROOM -> {
                // Close Room database connections
                try {
                    roomDatabases.closeAll()
                } catch (_: Exception) {
                    // Ignore if databases were not opened
                }

                // Stop Koin if it was started
                try {
                    stopKoin()
                } catch (_: Exception) {
                    // Ignore if Koin was not started
                }
            }
        }

        // Clean up temporary directory
        tempDir.deleteRecursively()

        // Clean up mocks
        unmockkObject(Encryption)
    }

    /**
     * Creates a second test user for testing user-specific data isolation
     */
    private fun createSecondTestUser(): User =
        User(
            id = "test-user-2-id",
            name = "Test User 2",
            email = "test2@example.com",
            encryptionKey = "12345678901234567890123456789012", // 32 chars
            accessToken = "fake-access-token-for-testing-2",
        )

    @ParameterizedTest(name = "test create and retrieve proxy group - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test create and retrieve proxy group`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val testProxy =
                    Proxy(
                        id = UUID.randomUUID(),
                        address = "127.0.0.1",
                        port = 8080,
                        username = "proxyuser",
                        password = "proxypass",
                    )
                val proxyGroup =
                    ProxyGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Proxy Group",
                        numberOfItems = 1,
                        items = sequenceOf(testProxy),
                    )

                // When
                proxyDataSource.createProxyGroup(testUser, proxyGroup)

                // Then
                val retrievedGroups = proxyDataSource.getProxyGroups(testUser).first()
                assertEquals(1, retrievedGroups.size)

                val retrievedGroup = retrievedGroups.first()
                assertEquals(proxyGroup.id, retrievedGroup.id)
                assertEquals(proxyGroup.name, retrievedGroup.name)
                assertEquals(1, retrievedGroup.numberOfItems)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test update proxy group name - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test update proxy group name`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val proxyGroup =
                    ProxyGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Original Name",
                        numberOfItems = 0,
                        items = emptySequence(),
                    )
                proxyDataSource.createProxyGroup(testUser, proxyGroup)

                // When
                proxyDataSource.updateProxyGroup(testUser, proxyGroup.id, "Updated Name")

                // Then
                val retrievedGroups = proxyDataSource.getProxyGroups(testUser).first()
                val retrievedGroup = retrievedGroups.first { it.id == proxyGroup.id }
                assertEquals("Updated Name", retrievedGroup.name)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test delete proxy group - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test delete proxy group`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val proxyGroup =
                    ProxyGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        items = emptySequence(),
                    )
                proxyDataSource.createProxyGroup(testUser, proxyGroup)

                // When
                proxyDataSource.deleteProxyGroup(testUser, proxyGroup.id)

                // Then
                val retrievedGroups = proxyDataSource.getProxyGroups(testUser).first()
                assertTrue(retrievedGroups.isEmpty())
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test create and retrieve proxy in group - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test create and retrieve proxy in group`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val proxyGroup =
                    ProxyGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        items = emptySequence(),
                    )
                proxyDataSource.createProxyGroup(testUser, proxyGroup)

                val testProxy =
                    Proxy(
                        id = UUID.randomUUID(),
                        address = "192.168.1.1",
                        port = 3128,
                        username = "testuser",
                        password = "testpass",
                    )

                // When
                proxyDataSource.updateOrCreateProxy(testUser, testProxy, proxyGroup.id)

                // Then
                val proxiesInGroup = proxyDataSource.getProxiesFromGroup(testUser, proxyGroup.id)
                assertEquals(1, proxiesInGroup.size)

                val retrievedProxy = proxiesInGroup.first()
                assertEquals(testProxy.id, retrievedProxy.id)
                assertEquals(testProxy.address, retrievedProxy.address)
                assertEquals(testProxy.port, retrievedProxy.port)
                assertEquals(testProxy.username, retrievedProxy.username)
                assertEquals(testProxy.password, retrievedProxy.password)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test get proxies count in group - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test get proxies count in group`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val proxyGroup =
                    ProxyGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        items = emptySequence(),
                    )
                proxyDataSource.createProxyGroup(testUser, proxyGroup)

                val proxy1 = Proxy(UUID.randomUUID(), "192.168.1.1", 3128, "user1", "pass1")
                val proxy2 = Proxy(UUID.randomUUID(), "192.168.1.2", 3128, "user2", "pass2")

                // When
                proxyDataSource.updateOrCreateProxy(testUser, proxy1, proxyGroup.id)
                proxyDataSource.updateOrCreateProxy(testUser, proxy2, proxyGroup.id)

                // Then
                val count = proxyDataSource.getProxiesInGroupCount(testUser, proxyGroup.id)
                assertEquals(2, count)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test get proxies flow - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test get proxies flow`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val proxyGroup =
                    ProxyGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        items = emptySequence(),
                    )
                proxyDataSource.createProxyGroup(testUser, proxyGroup)

                val testProxy =
                    Proxy(
                        id = UUID.randomUUID(),
                        address = "10.0.0.1",
                        port = 8080,
                        username = "flowuser",
                        password = "flowpass",
                    )

                // When
                proxyDataSource.updateOrCreateProxy(testUser, testProxy, proxyGroup.id)

                // Then
                val proxiesFlow = proxyDataSource.getProxiesFlow(testUser, proxyGroup.id).first()
                assertEquals(1, proxiesFlow.size)
                assertEquals(testProxy.id, proxiesFlow.first().id)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test update existing proxy - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test update existing proxy`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val proxyGroup =
                    ProxyGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        items = emptySequence(),
                    )
                proxyDataSource.createProxyGroup(testUser, proxyGroup)

                val originalProxy =
                    Proxy(
                        id = UUID.randomUUID(),
                        address = "original.com",
                        port = 8080,
                        username = "original",
                        password = "original",
                    )
                proxyDataSource.updateOrCreateProxy(testUser, originalProxy, proxyGroup.id)

                // When
                val updatedProxy =
                    originalProxy.copy(
                        address = "updated.com",
                        port = 9090,
                        username = "updated",
                        password = "updated",
                    )
                proxyDataSource.updateOrCreateProxy(testUser, updatedProxy, proxyGroup.id)

                // Then
                val proxiesInGroup = proxyDataSource.getProxiesFromGroup(testUser, proxyGroup.id)
                assertEquals(1, proxiesInGroup.size)

                val retrievedProxy = proxiesInGroup.first()
                assertEquals("updated.com", retrievedProxy.address)
                assertEquals(9090, retrievedProxy.port)
                assertEquals("updated", retrievedProxy.username)
                assertEquals("updated", retrievedProxy.password)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test delete proxy - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test delete proxy`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val proxyGroup =
                    ProxyGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        items = emptySequence(),
                    )
                proxyDataSource.createProxyGroup(testUser, proxyGroup)

                val testProxy =
                    Proxy(
                        id = UUID.randomUUID(),
                        address = "delete.me",
                        port = 8080,
                        username = "deleteuser",
                        password = "deletepass",
                    )
                proxyDataSource.updateOrCreateProxy(testUser, testProxy, proxyGroup.id)

                // When
                proxyDataSource.deleteProxy(testUser, testProxy.id)

                // Then
                val proxiesInGroup = proxyDataSource.getProxiesFromGroup(testUser, proxyGroup.id)
                assertTrue(proxiesInGroup.isEmpty())

                val proxiesCount = proxyDataSource.getProxiesInGroupCount(testUser, proxyGroup.id)
                assertEquals(0, proxiesCount)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test user data isolation - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test user data isolation`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val user2 = createSecondTestUser()
                val proxyGroup =
                    ProxyGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        items = emptySequence(),
                    )

                // When
                proxyDataSource.createProxyGroup(testUser, proxyGroup)

                // Then
                userScopeProvider.currentScope = GlobalContext.get().getScope(testUser.id)
                val user1Groups = proxyDataSource.getProxyGroups(testUser).first()
                userScopeProvider.currentScope = GlobalContext.get().getScope(user2.id)
                val user2Groups = proxyDataSource.getProxyGroups(user2).first()

                assertEquals(1, user1Groups.size)
                assertEquals(0, user2Groups.size)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test delete all proxies from group - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test delete all proxies from group`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val proxyGroup =
                    ProxyGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        items = emptySequence(),
                    )
                proxyDataSource.createProxyGroup(testUser, proxyGroup)

                val proxy1 = Proxy(UUID.randomUUID(), "proxy1.com", 8080, "user1", "pass1")
                val proxy2 = Proxy(UUID.randomUUID(), "proxy2.com", 8080, "user2", "pass2")
                proxyDataSource.updateOrCreateProxy(testUser, proxy1, proxyGroup.id)
                proxyDataSource.updateOrCreateProxy(testUser, proxy2, proxyGroup.id)

                // When
                proxyDataSource.deleteAllProxiesFromGroup(testUser, proxyGroup.id)

                // Then
                val proxiesCount = proxyDataSource.getProxiesInGroupCount(testUser, proxyGroup.id)
                assertEquals(0, proxiesCount)

                val proxiesInGroup = proxyDataSource.getProxiesFromGroup(testUser, proxyGroup.id)
                assertTrue(proxiesInGroup.isEmpty())
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test proxy with null credentials - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test proxy with null credentials`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val proxyGroup =
                    ProxyGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        items = emptySequence(),
                    )
                proxyDataSource.createProxyGroup(testUser, proxyGroup)

                val proxyWithoutAuth =
                    Proxy(
                        id = UUID.randomUUID(),
                        address = "no-auth.proxy.com",
                        port = 8080,
                        username = null,
                        password = null,
                    )

                // When
                proxyDataSource.updateOrCreateProxy(testUser, proxyWithoutAuth, proxyGroup.id)

                // Then
                val proxiesInGroup = proxyDataSource.getProxiesFromGroup(testUser, proxyGroup.id)
                assertEquals(1, proxiesInGroup.size)

                val retrievedProxy = proxiesInGroup.first()
                assertEquals(proxyWithoutAuth.address, retrievedProxy.address)
                assertEquals(proxyWithoutAuth.port, retrievedProxy.port)
                assertNull(retrievedProxy.username)
                assertNull(retrievedProxy.password)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test get proxy by id - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test get proxy by id`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val proxyGroup =
                    ProxyGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        items = emptySequence(),
                    )
                proxyDataSource.createProxyGroup(testUser, proxyGroup)

                val testProxy =
                    Proxy(
                        id = UUID.randomUUID(),
                        address = "test.proxy.com",
                        port = 8080,
                        username = "testuser",
                        password = "testpass",
                    )
                proxyDataSource.updateOrCreateProxy(testUser, testProxy, proxyGroup.id)

                // When
                val retrievedProxy = proxyDataSource.getProxy(testUser, testProxy.id.toString())

                // Then
                assertNotNull(retrievedProxy)
                assertEquals(testProxy.id, retrievedProxy!!.id)
                assertEquals(testProxy.address, retrievedProxy.address)
                assertEquals(testProxy.port, retrievedProxy.port)
                assertEquals(testProxy.username, retrievedProxy.username)
                assertEquals(testProxy.password, retrievedProxy.password)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test get proxy group by id - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test get proxy group by id`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val testProxy =
                    Proxy(
                        id = UUID.randomUUID(),
                        address = "group.test.proxy.com",
                        port = 8080,
                        username = "groupuser",
                        password = "grouppass",
                    )
                val proxyGroup =
                    ProxyGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 1,
                        items = sequenceOf(testProxy),
                    )
                proxyDataSource.createProxyGroup(testUser, proxyGroup)

                // When
                val retrievedGroup = proxyDataSource.getProxyGroup(testUser, proxyGroup.id)

                // Then
                assertNotNull(retrievedGroup)
                assertEquals(proxyGroup.id, retrievedGroup!!.id)
                assertEquals(proxyGroup.name, retrievedGroup.name)
                assertEquals(1, retrievedGroup.numberOfItems)
            } finally {
                tearDownForImplementation(implementation)
            }
        }
}

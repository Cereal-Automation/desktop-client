package com.cereal.client.infrastructure.data.datasource.database.integration

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.proxy.ProxyProviderConnector
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.room.DatabaseConnector
import com.cereal.client.infrastructure.data.datasource.database.room.RoomDatabases
import com.cereal.client.infrastructure.data.datasource.database.room.RoomProxyProviderConnectorDataSource
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Integration test for [RoomProxyProviderConnectorDataSource] against a real in-memory Room database,
 * exercising the connector store contract (upsert → observe → delete).
 */
@OptIn(ExperimentalTime::class)
class ProxyProviderConnectorDataSourceIntegrationTest {
    private lateinit var dataSource: RoomProxyProviderConnectorDataSource
    private lateinit var roomDatabases: RoomDatabases
    private lateinit var tempDir: File
    private lateinit var testUser: User

    private val userScopeProvider =
        object : UserScopeProvider {
            override var currentScope: Scope? = null
        }

    @BeforeEach
    fun setUp() {
        tempDir =
            File.createTempFile("test", "").apply {
                delete()
                mkdirs()
            }

        val applicationConfig =
            mockk<ApplicationConfig> {
                every { databaseDirectory } returns tempDir
                every { databaseName } returns "test.db"
                every { databaseEncryptionKey } returns "12345678901234567890123456789012"
            }

        val testKeyBytes = "12345678901234567890123456789012".toByteArray()
        mockkObject(Encryption)
        every { Encryption.getEncryptionKey(any(), any(), any()) } returns EncryptionKey(testKeyBytes, testKeyBytes)

        roomDatabases = RoomDatabases(applicationConfig)

        val koinApp =
            startKoin {
                modules(
                    module {
                        single { roomDatabases }
                        single<UserScopeProvider> { userScopeProvider }
                        scope(named("test-user-id")) {
                            scoped<UserRoomDatabase> { DatabaseConnector.connectUser(tempDir, "test-user-id") }
                            scoped(named("UserEncryptionKey")) { EncryptionKey(testKeyBytes, testKeyBytes) }
                        }
                    },
                )
            }

        userScopeProvider.currentScope = koinApp.koin.createScope("test-user-id", named("test-user-id"))
        dataSource = RoomProxyProviderConnectorDataSource(roomDatabases)
        testUser = User(id = "test-user-id", name = "Test", email = "t@e.com", encryptionKey = "12345678901234567890123456789012", accessToken = "tok")
    }

    @AfterEach
    fun tearDown() {
        runCatching { roomDatabases.closeAll() }
        runCatching { stopKoin() }
        tempDir.deleteRecursively()
        unmockkObject(Encryption)
    }

    @Test
    fun `upsert then observe round-trips the connector record`() =
        runTest {
            val connector = connector(subUserHash = "hash_a", traffic = 84.2, subUsers = 3)

            dataSource.upsertConnector(testUser, connector)

            val stored = dataSource.observeConnector(testUser, ProxyVendor.MARSPROXIES).first()
            assertEquals("hash_a", stored?.subUserHash)
            assertEquals(84.2, stored?.availableTrafficGb)
            assertEquals(3, stored?.subUserCount)
        }

    @Test
    fun `upsert overwrites the existing record for the same provider`() =
        runTest {
            dataSource.upsertConnector(testUser, connector(subUserHash = "old", traffic = 10.0, subUsers = 1))
            dataSource.upsertConnector(testUser, connector(subUserHash = "new", traffic = 20.0, subUsers = 2))

            val stored = dataSource.observeConnector(testUser, ProxyVendor.MARSPROXIES).first()
            assertEquals("new", stored?.subUserHash)
            assertEquals(2, stored?.subUserCount)
        }

    @Test
    fun `delete removes the connector record`() =
        runTest {
            dataSource.upsertConnector(testUser, connector(subUserHash = "hash_a", traffic = 84.2, subUsers = 3))

            dataSource.deleteConnector(testUser, ProxyVendor.MARSPROXIES)

            assertNull(dataSource.observeConnector(testUser, ProxyVendor.MARSPROXIES).first())
        }

    private fun connector(
        subUserHash: String,
        traffic: Double,
        subUsers: Int,
    ) = ProxyProviderConnector(
        provider = ProxyVendor.MARSPROXIES,
        connectedAt = Instant.fromEpochMilliseconds(1_000),
        lastSyncAt = Instant.fromEpochMilliseconds(2_000),
        subUserHash = subUserHash,
        availableTrafficGb = traffic,
        subUserCount = subUsers,
        credentialKey = "key_mars_proxies_api_token",
    )
}

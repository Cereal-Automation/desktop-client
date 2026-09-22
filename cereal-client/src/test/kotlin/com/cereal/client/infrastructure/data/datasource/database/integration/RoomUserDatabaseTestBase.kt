package com.cereal.client.infrastructure.data.datasource.database.integration

import androidx.room.execSQL
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.room.DatabaseConnector
import com.cereal.client.infrastructure.data.datasource.database.room.RoomDatabases
import com.cereal.client.infrastructure.data.datasource.database.room.UserRoomDatabase
import com.cereal.client.infrastructure.data.datasource.filesystem.security.Encryption
import com.cereal.client.infrastructure.data.datasource.filesystem.security.EncryptionKey
import com.cereal.client.infrastructure.di.UserScopeProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import java.io.File
import java.util.UUID

/**
 * Shared harness for integration tests that exercise a user-scoped [UserRoomDatabase] against a real
 * (in-memory backed, on-disk temp) SQLite database — the seam called out in AGENTS.md for the
 * "Infra: Room data source" layer.
 *
 * It wires up the same Koin graph the production code expects (a [RoomDatabases] single, a user
 * [Scope] holding the [UserRoomDatabase] and its `UserEncryptionKey`, and a [UserScopeProvider]),
 * and mocks [Encryption] so field-level [EncryptedString][com.cereal.client.infrastructure.data.datasource.database.room.type.EncryptedString]
 * encryption round-trips with a deterministic test key.
 */
abstract class RoomUserDatabaseTestBase {
    protected lateinit var roomDatabases: RoomDatabases
    protected lateinit var testUser: User
    private lateinit var tempDir: File

    protected val userScopeProvider = TestUserScopeProvider()

    class TestUserScopeProvider : UserScopeProvider {
        override var currentScope: Scope? = null
    }

    @BeforeEach
    fun setUpDatabase() {
        tempDir =
            File.createTempFile("test", "").apply {
                delete()
                mkdirs()
            }

        val applicationConfig =
            mockk<ApplicationConfig> {
                every { databaseDirectory } returns tempDir
                every { databaseName } returns "test.db"
                every { databaseEncryptionKey } returns TEST_KEY
            }

        val testKeyBytes = TEST_KEY.toByteArray()
        mockkObject(Encryption)
        every { Encryption.getEncryptionKey(any(), any(), any()) } returns EncryptionKey(testKeyBytes, testKeyBytes)

        roomDatabases = RoomDatabases(applicationConfig)

        val koinApp =
            startKoin {
                modules(
                    module {
                        single { roomDatabases }
                        single(named("applicationEncryptionKey")) { TEST_KEY }
                        single<UserScopeProvider> { userScopeProvider }

                        scope(named(USER_ID)) {
                            scoped<UserRoomDatabase> { DatabaseConnector.connectUser(tempDir, USER_ID) }
                            scoped(named("UserEncryptionKey")) { EncryptionKey(testKeyBytes, testKeyBytes) }
                        }
                    },
                )
            }

        userScopeProvider.currentScope = koinApp.koin.createScope(USER_ID, named(USER_ID))

        testUser =
            User(
                id = USER_ID,
                name = "Test User",
                email = "test@example.com",
                encryptionKey = TEST_KEY,
                accessToken = "fake-access-token-for-testing",
            )
    }

    @AfterEach
    fun tearDownDatabase() {
        try {
            roomDatabases.closeAll()
        } catch (_: Exception) {
            // Ignore if databases were not opened
        }
        try {
            stopKoin()
        } catch (_: Exception) {
            // Ignore if Koin was not started
        }
        if (::tempDir.isInitialized) {
            tempDir.deleteRecursively()
        }
        unmockkObject(Encryption)
    }

    /**
     * Seeds the foreign-key parent chain (script package group → package → script → task) so rows
     * that reference a task (log events, notification history) can be inserted. Returns the seeded
     * task id. Timestamps are irrelevant to these tests, so they default to 0.
     */
    protected suspend fun seedTask(taskId: String = UUID.randomUUID().toString()): String {
        val groupId = UUID.randomUUID().toString()
        val packageId = UUID.randomUUID().toString()
        val scriptId = UUID.randomUUID().toString()
        val db = roomDatabases.getUserDatabase(testUser)
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                execSQL(
                    "INSERT INTO script_package_group (id, name, created_at, updated_at) " +
                        "VALUES ('$groupId', 'Test Group', 0, 0)",
                )
                execSQL(
                    "INSERT INTO script_package " +
                        "(id, group_id, package_name, number_of_concurrent_tasks, created_at, updated_at) " +
                        "VALUES ('$packageId', '$groupId', 'com.test.script', NULL, 0, 0)",
                )
                execSQL(
                    "INSERT INTO script (id, package_id, script_id, parent_script_id, created_at, updated_at) " +
                        "VALUES ('$scriptId', '$packageId', NULL, NULL, 0, 0)",
                )
                execSQL(
                    "INSERT INTO task (id, script_id, created_at, updated_at) " +
                        "VALUES ('$taskId', '$scriptId', 0, 0)",
                )
            }
        }
        return taskId
    }

    companion object {
        const val USER_ID = "test-user-id"
        private const val TEST_KEY = "12345678901234567890123456789012" // 32 chars
    }
}

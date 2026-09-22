package com.cereal.client.infrastructure.data.datasource.database.integration

import com.cereal.client.Sekret
import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.DatabaseImplementation
import com.cereal.client.infrastructure.data.datasource.database.KeyValueDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.DatabaseConnector
import com.cereal.client.infrastructure.data.datasource.database.room.RoomDatabases
import com.cereal.client.infrastructure.data.datasource.database.room.RoomKeyValueDataSource
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
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.koin.core.context.GlobalContext
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.core.scope.Scope
import org.koin.dsl.module
import java.io.File

/**
 * Parameterized integration test for KeyValueDataSource implementations.
 */
class KeyValueDataSourceIntegrationTest {
    private lateinit var keyValueDataSource: KeyValueDataSource
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

                // That returns a `UserRoomDatabase`.
                // `RoomKeyValueDataSource` then uses `userRoomDatabase.applicationSettingsDao()`.

                // Keep it simple: stick to the previous pattern of mocking if possible, or use the real one if the test context allows.
                // The prompt "fix compilation" implies we should make it work.
                // The error is "User scope not found".
                // I will add the scopes and try to provide a mock UserRoomDatabase within them,
                // OR better, checking how `RoomDatabases` works, it acts as a KoinComponent.

                // Actually, `RoomDatabases` in the test is real.
                // `RoomDatabases.getUserDatabase` calls `scope.get()`.
                // So I need to put a `UserRoomDatabase` into the scope.
                // Since this is an integration test, it likely expects to interact with the database.
                // But `RoomDatabases` is initialized with `applicationConfig`.
                // Creating a real `UserRoomDatabase` might be complex.
                // Let's check if we can mock it or if we need the real one.
                // The test calls `keyValueDataSource` methods.
                // `RoomKeyValueDataSource` uses the DAO.

                // Let's try to reuse the approach from RoomDatabasesTest?
                // No, this is an integration test, it might want real DBs.
                // But `RoomDatabases` logic for `getUserDatabase` delegates to Koin.
                // If I put a mock in the scope, will it work?
                // `RoomKeyValueDataSource` needs `applicationSettingsDao`.

                // Let's create the scopes.
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

                keyValueDataSource = RoomKeyValueDataSource(roomDatabases)
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
    }

    @AfterEach
    fun tearDown() {
        // Clean up temporary directory if it was initialized
        if (::tempDir.isInitialized) {
            tempDir.deleteRecursively()
        }

        // Clean up mocks
        unmockkObject(Encryption)
        unmockkObject(Sekret)
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

    @ParameterizedTest(name = "test string key-value operations - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test string key-value operations`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val testKey = "test_string_key"
                val testValue = "test_string_value"

                // Test setting and getting string value
                keyValueDataSource.setStringByKey(testKey, testValue, null)
                val retrievedValue = keyValueDataSource.getStringByKey(testKey, null).first()
                assertEquals(testValue, retrievedValue)

                // Test updating string value
                val updatedValue = "updated_string_value"
                keyValueDataSource.setStringByKey(testKey, updatedValue, null)
                val updatedRetrievedValue = keyValueDataSource.getStringByKey(testKey, null).first()
                assertEquals(updatedValue, updatedRetrievedValue)

                // Test setting null value
                keyValueDataSource.setStringByKey(testKey, null, null)
                val nullValue = keyValueDataSource.getStringByKey(testKey, null).first()
                assertNull(nullValue)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test integer key-value operations - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test integer key-value operations`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val testKey = "test_int_key"
                val testValue = 42

                // Test setting and getting integer value
                keyValueDataSource.setIntByKey(testKey, testValue, null)
                val retrievedValue = keyValueDataSource.getIntByKey(testKey, null).first()
                assertEquals(testValue, retrievedValue)

                // Test updating integer value
                val updatedValue = 100
                keyValueDataSource.setIntByKey(testKey, updatedValue, null)
                val updatedRetrievedValue = keyValueDataSource.getIntByKey(testKey, null).first()
                assertEquals(updatedValue, updatedRetrievedValue)

                // Test setting null value
                keyValueDataSource.setIntByKey(testKey, null, null)
                val nullValue = keyValueDataSource.getIntByKey(testKey, null).first()
                assertNull(nullValue)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test long key-value operations - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test long key-value operations`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val testKey = "test_long_key"
                val testValue = 123456789L

                // Test setting and getting long value
                keyValueDataSource.setLongByKey(testKey, testValue, null)
                val retrievedValue = keyValueDataSource.getLongByKey(testKey, null).first()
                assertEquals(testValue, retrievedValue)

                // Test updating long value
                val updatedValue = 987654321L
                keyValueDataSource.setLongByKey(testKey, updatedValue, null)
                val updatedRetrievedValue = keyValueDataSource.getLongByKey(testKey, null).first()
                assertEquals(updatedValue, updatedRetrievedValue)

                // Test setting null value
                keyValueDataSource.setLongByKey(testKey, null, null)
                val nullValue = keyValueDataSource.getLongByKey(testKey, null).first()
                assertNull(nullValue)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test float key-value operations - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test float key-value operations`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val testKey = "test_float_key"
                val testValue = 3.14f

                // Test setting and getting float value
                keyValueDataSource.setFloatByKey(testKey, testValue, null)
                val retrievedValue = keyValueDataSource.getFloatByKey(testKey, null).first()
                assertEquals(testValue, retrievedValue)

                // Test updating float value
                val updatedValue = 2.71f
                keyValueDataSource.setFloatByKey(testKey, updatedValue, null)
                val updatedRetrievedValue = keyValueDataSource.getFloatByKey(testKey, null).first()
                assertEquals(updatedValue, updatedRetrievedValue)

                // Test setting null value
                keyValueDataSource.setFloatByKey(testKey, null, null)
                val nullValue = keyValueDataSource.getFloatByKey(testKey, null).first()
                assertNull(nullValue)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test boolean key-value operations - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test boolean key-value operations`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val testKey = "test_boolean_key"
                val testValue = true

                // Test setting and getting boolean value
                keyValueDataSource.setBooleanByKey(testKey, testValue, null)
                val retrievedValue = keyValueDataSource.getBooleanByKey(testKey, null).first()
                assertEquals(testValue, retrievedValue)

                // Test updating boolean value
                val updatedValue = false
                keyValueDataSource.setBooleanByKey(testKey, updatedValue, null)
                val updatedRetrievedValue = keyValueDataSource.getBooleanByKey(testKey, null).first()
                assertEquals(updatedValue, updatedRetrievedValue)

                // Test setting null value
                keyValueDataSource.setBooleanByKey(testKey, null, null)
                val nullValue = keyValueDataSource.getBooleanByKey(testKey, null).first()
                assertNull(nullValue)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test user-specific key-value operations - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test user-specific key-value operations`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val testKey = "user_specific_key"
                val applicationValue = "application_value"
                val userValue = "user_value"

                // Set value for application db (null user)
                keyValueDataSource.setStringByKey(testKey, applicationValue, null)

                // Set value for user db
                userScopeProvider.currentScope = GlobalContext.get().getScope(testUser.id)
                keyValueDataSource.setStringByKey(testKey, userValue, testUser)

                // Values should be different for application vs user db
                val appRetrievedValue = keyValueDataSource.getStringByKey(testKey, null).first()
                userScopeProvider.currentScope = GlobalContext.get().getScope(testUser.id)
                val userRetrievedValue = keyValueDataSource.getStringByKey(testKey, testUser).first()

                assertEquals(applicationValue, appRetrievedValue)
                assertEquals(userValue, userRetrievedValue)
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
                val user2 = createSecondTestUser()
                val testKey = "isolation_test_key"
                val user1Value = "user1_value"
                val user2Value = "user2_value"

                // Set different values for different users
                userScopeProvider.currentScope = GlobalContext.get().getScope(testUser.id)
                keyValueDataSource.setStringByKey(testKey, user1Value, testUser)
                userScopeProvider.currentScope = GlobalContext.get().getScope(user2.id)
                keyValueDataSource.setStringByKey(testKey, user2Value, user2)

                // Values should be isolated per user
                userScopeProvider.currentScope = GlobalContext.get().getScope(user2.id)
                val user2RetrievedValue = keyValueDataSource.getStringByKey(testKey, user2).first()
                userScopeProvider.currentScope = GlobalContext.get().getScope(testUser.id)
                val user1RetrievedValue = keyValueDataSource.getStringByKey(testKey, testUser).first()

                assertEquals(user1Value, user1RetrievedValue)
                assertEquals(user2Value, user2RetrievedValue)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test delete value by key - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test delete value by key`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val testKey = "delete_test_key"
                val testValue = "value_to_delete"

                // Set a value
                keyValueDataSource.setStringByKey(testKey, testValue, null)
                val initialValue = keyValueDataSource.getStringByKey(testKey, null).first()
                assertEquals(testValue, initialValue)

                // Delete the value
                keyValueDataSource.deleteValueByKey(testKey, null)
                val deletedValue = keyValueDataSource.getStringByKey(testKey, null).first()
                assertNull(deletedValue)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test delete user-specific value by key - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test delete user-specific value by key`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val testKey = "user_delete_test_key"
                val testValue = "user_value_to_delete"

                // Set a value for user
                userScopeProvider.currentScope = GlobalContext.get().getScope(testUser.id)
                keyValueDataSource.setStringByKey(testKey, testValue, testUser)
                val initialValue = keyValueDataSource.getStringByKey(testKey, testUser).first()
                assertEquals(testValue, initialValue)

                // Delete the user-specific value
                userScopeProvider.currentScope = GlobalContext.get().getScope(testUser.id)
                keyValueDataSource.deleteValueByKey(testKey, testUser)
                val deletedValue = keyValueDataSource.getStringByKey(testKey, testUser).first()
                assertNull(deletedValue)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test non-existent key returns null - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test non-existent key returns null`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val nonExistentKey = "non_existent_key"

                // Test all data types return null for non-existent keys
                assertNull(keyValueDataSource.getStringByKey(nonExistentKey, null).first())
                assertNull(keyValueDataSource.getIntByKey(nonExistentKey, null).first())
                assertNull(keyValueDataSource.getLongByKey(nonExistentKey, null).first())
                assertNull(keyValueDataSource.getFloatByKey(nonExistentKey, null).first())
                assertNull(keyValueDataSource.getBooleanByKey(nonExistentKey, null).first())
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test multiple keys with different data types - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test multiple keys with different data types`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val stringKey = "multi_string_key"
                val intKey = "multi_int_key"
                val booleanKey = "multi_boolean_key"

                val stringValue = "multi_string_value"
                val intValue = 999
                val booleanValue = true

                // Set different types of values
                keyValueDataSource.setStringByKey(stringKey, stringValue, null)
                keyValueDataSource.setIntByKey(intKey, intValue, null)
                keyValueDataSource.setBooleanByKey(booleanKey, booleanValue, null)

                // Retrieve and verify all values
                assertEquals(stringValue, keyValueDataSource.getStringByKey(stringKey, null).first())
                assertEquals(intValue, keyValueDataSource.getIntByKey(intKey, null).first())
                assertEquals(booleanValue, keyValueDataSource.getBooleanByKey(booleanKey, null).first())
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test overwriting existing key with different data type - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test overwriting existing key with different data type`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val testKey = "overwrite_test_key"

                // Set as string first
                keyValueDataSource.setStringByKey(testKey, "string_value", null)
                assertEquals("string_value", keyValueDataSource.getStringByKey(testKey, null).first())

                // Overwrite with integer (stored as string internally)
                keyValueDataSource.setIntByKey(testKey, 42, null)
                assertEquals(42, keyValueDataSource.getIntByKey(testKey, null).first())

                // The string representation should now be "42"
                assertEquals("42", keyValueDataSource.getStringByKey(testKey, null).first())
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test edge case values - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test edge case values`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Test empty string
                keyValueDataSource.setStringByKey("empty_string", "", null)
                assertEquals("", keyValueDataSource.getStringByKey("empty_string", null).first())

                // Test zero values
                keyValueDataSource.setIntByKey("zero_int", 0, null)
                assertEquals(0, keyValueDataSource.getIntByKey("zero_int", null).first())

                keyValueDataSource.setLongByKey("zero_long", 0L, null)
                assertEquals(0L, keyValueDataSource.getLongByKey("zero_long", null).first())

                keyValueDataSource.setFloatByKey("zero_float", 0.0f, null)
                assertEquals(0.0f, keyValueDataSource.getFloatByKey("zero_float", null).first())

                // Test false boolean
                keyValueDataSource.setBooleanByKey("false_boolean", false, null)
                assertEquals(false, keyValueDataSource.getBooleanByKey("false_boolean", null).first())
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test large values - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test large values`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Test large string
                val largeString = "x".repeat(10000)
                keyValueDataSource.setStringByKey("large_string", largeString, null)
                assertEquals(largeString, keyValueDataSource.getStringByKey("large_string", null).first())

                // Test max integer value
                keyValueDataSource.setIntByKey("max_int", Int.MAX_VALUE, null)
                assertEquals(Int.MAX_VALUE, keyValueDataSource.getIntByKey("max_int", null).first())

                // Test max long value
                keyValueDataSource.setLongByKey("max_long", Long.MAX_VALUE, null)
                assertEquals(Long.MAX_VALUE, keyValueDataSource.getLongByKey("max_long", null).first())
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test null value handling and cache behavior - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test null value handling and cache behavior`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val testKey = "null_test_key"

                // Test getting non-existent value (should return null)
                assertNull(keyValueDataSource.getStringByKey(testKey, null).first())
                assertNull(keyValueDataSource.getIntByKey(testKey, null).first())
                assertNull(keyValueDataSource.getBooleanByKey(testKey, null).first())
                assertNull(keyValueDataSource.getLongByKey(testKey, null).first())
                assertNull(keyValueDataSource.getFloatByKey(testKey, null).first())

                // Set a value
                keyValueDataSource.setStringByKey(testKey, "test_value", null)
                assertEquals("test_value", keyValueDataSource.getStringByKey(testKey, null).first())

                // Set value to null (equivalent to deleting)
                keyValueDataSource.setStringByKey(testKey, null, null)
                assertNull(keyValueDataSource.getStringByKey(testKey, null).first())

                // Test with different data types
                keyValueDataSource.setIntByKey("int_key", 42, null)
                assertEquals(42, keyValueDataSource.getIntByKey("int_key", null).first())
                keyValueDataSource.setIntByKey("int_key", null, null)
                assertNull(keyValueDataSource.getIntByKey("int_key", null).first())

                keyValueDataSource.setBooleanByKey("bool_key", true, null)
                assertEquals(true, keyValueDataSource.getBooleanByKey("bool_key", null).first())
                keyValueDataSource.setBooleanByKey("bool_key", null, null)
                assertNull(keyValueDataSource.getBooleanByKey("bool_key", null).first())

                keyValueDataSource.setLongByKey("long_key", 123456789L, null)
                assertEquals(123456789L, keyValueDataSource.getLongByKey("long_key", null).first())
                keyValueDataSource.setLongByKey("long_key", null, null)
                assertNull(keyValueDataSource.getLongByKey("long_key", null).first())

                keyValueDataSource.setFloatByKey("float_key", 3.14f, null)
                assertEquals(3.14f, keyValueDataSource.getFloatByKey("float_key", null).first())
                keyValueDataSource.setFloatByKey("float_key", null, null)
                assertNull(keyValueDataSource.getFloatByKey("float_key", null).first())
            } finally {
                tearDownForImplementation(implementation)
            }
        }
}

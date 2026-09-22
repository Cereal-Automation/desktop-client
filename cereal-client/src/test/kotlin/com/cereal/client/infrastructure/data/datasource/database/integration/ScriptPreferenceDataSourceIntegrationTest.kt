package com.cereal.client.infrastructure.data.datasource.database.integration

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.DatabaseImplementation
import com.cereal.client.infrastructure.data.datasource.database.ScriptPreferenceDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.DatabaseConnector
import com.cereal.client.infrastructure.data.datasource.database.room.RoomDatabases
import com.cereal.client.infrastructure.data.datasource.database.room.RoomScriptPreferenceDataSource
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
 * Parameterized integration test for ScriptPreferenceDataSource implementations.
 */
class ScriptPreferenceDataSourceIntegrationTest {
    private lateinit var scriptPreferenceDataSource: ScriptPreferenceDataSource
    private lateinit var roomDatabases: RoomDatabases
    private lateinit var testUser: User
    private lateinit var tempDir: File

    private val userScopeProvider = TestUserScopeProvider()

    class TestUserScopeProvider : UserScopeProvider {
        override var currentScope: Scope? = null
    }

    /**
     * Sets up the test environment for a specific database implementation.
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
                                        .named("test-user-id-2"),
                                ) {
                                    scoped<UserRoomDatabase> {
                                        DatabaseConnector.connectUser(tempDir, "test-user-id-2")
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
                    "test-user-id-2",
                    org.koin.core.qualifier
                        .named("test-user-id-2"),
                )

                // Set default scope
                userScopeProvider.currentScope = user1Scope

                scriptPreferenceDataSource = RoomScriptPreferenceDataSource(roomDatabases)
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

    private val testInstanceId = "test-script-instance-id-1"
    private val testInstanceId2 = "test-script-instance-id-2"

    @ParameterizedTest(name = "test string preference operations - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test string preference operations`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val testKey = "test_string_preference"
                val testValue = "test_string_value"

                // Test setting and getting string preference
                scriptPreferenceDataSource.setString(testUser, testInstanceId, testKey, testValue)
                val retrievedValue = scriptPreferenceDataSource.getString(testUser, testInstanceId, testKey).first()
                assertEquals(testValue, retrievedValue)

                // Test updating string preference
                val updatedValue = "updated_string_value"
                scriptPreferenceDataSource.setString(testUser, testInstanceId, testKey, updatedValue)
                val updatedRetrievedValue =
                    scriptPreferenceDataSource.getString(testUser, testInstanceId, testKey).first()
                assertEquals(updatedValue, updatedRetrievedValue)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test integer preference operations - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test integer preference operations`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val testKey = "test_int_preference"
                val testValue = 42

                // Test setting and getting integer preference
                scriptPreferenceDataSource.setInt(testUser, testInstanceId, testKey, testValue)
                val retrievedValue = scriptPreferenceDataSource.getInt(testUser, testInstanceId, testKey).first()
                assertEquals(testValue, retrievedValue)

                // Test updating integer preference
                val updatedValue = 100
                scriptPreferenceDataSource.setInt(testUser, testInstanceId, testKey, updatedValue)
                val updatedRetrievedValue = scriptPreferenceDataSource.getInt(testUser, testInstanceId, testKey).first()
                assertEquals(updatedValue, updatedRetrievedValue)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test long preference operations - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test long preference operations`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val testKey = "test_long_preference"
                val testValue = 123456789L

                // Test setting and getting long preference
                scriptPreferenceDataSource.setLong(testUser, testInstanceId, testKey, testValue)
                val retrievedValue = scriptPreferenceDataSource.getLong(testUser, testInstanceId, testKey).first()
                assertEquals(testValue, retrievedValue)

                // Test updating long preference
                val updatedValue = 987654321L
                scriptPreferenceDataSource.setLong(testUser, testInstanceId, testKey, updatedValue)
                val updatedRetrievedValue =
                    scriptPreferenceDataSource.getLong(testUser, testInstanceId, testKey).first()
                assertEquals(updatedValue, updatedRetrievedValue)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test float preference operations - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test float preference operations`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val testKey = "test_float_preference"
                val testValue = 3.14f

                // Test setting and getting float preference
                scriptPreferenceDataSource.setFloat(testUser, testInstanceId, testKey, testValue)
                val retrievedValue = scriptPreferenceDataSource.getFloat(testUser, testInstanceId, testKey).first()
                assertEquals(testValue, retrievedValue)

                // Test updating float preference
                val updatedValue = 2.71f
                scriptPreferenceDataSource.setFloat(testUser, testInstanceId, testKey, updatedValue)
                val updatedRetrievedValue =
                    scriptPreferenceDataSource.getFloat(testUser, testInstanceId, testKey).first()
                assertEquals(updatedValue, updatedRetrievedValue)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test boolean preference operations - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test boolean preference operations`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val testKey = "test_boolean_preference"
                val testValue = true

                // Test setting and getting boolean preference
                scriptPreferenceDataSource.setBoolean(testUser, testInstanceId, testKey, testValue)
                val retrievedValue = scriptPreferenceDataSource.getBoolean(testUser, testInstanceId, testKey).first()
                assertEquals(testValue, retrievedValue)

                // Test updating boolean preference
                val updatedValue = false
                scriptPreferenceDataSource.setBoolean(testUser, testInstanceId, testKey, updatedValue)
                val updatedRetrievedValue =
                    scriptPreferenceDataSource.getBoolean(testUser, testInstanceId, testKey).first()
                assertEquals(updatedValue, updatedRetrievedValue)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test instance isolation - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test instance isolation`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val testKey = "isolation_test_key"
                val instance1Value = "instance1_value"
                val instance2Value = "instance2_value"

                // Set different values for different instances
                scriptPreferenceDataSource.setString(testUser, testInstanceId, testKey, instance1Value)
                scriptPreferenceDataSource.setString(testUser, testInstanceId2, testKey, instance2Value)

                // Values should be isolated per instance
                val instance1RetrievedValue =
                    scriptPreferenceDataSource.getString(testUser, testInstanceId, testKey).first()
                val instance2RetrievedValue =
                    scriptPreferenceDataSource.getString(testUser, testInstanceId2, testKey).first()

                assertEquals(instance1Value, instance1RetrievedValue)
                assertEquals(instance2Value, instance2RetrievedValue)
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
                val testKey = "user_isolation_test_key"
                val user1Value = "user1_value"
                val user2Value = "user2_value"

                // Set different values for different users with same instance and key
                scriptPreferenceDataSource.setString(testUser, testInstanceId, testKey, user1Value)
                scriptPreferenceDataSource.setString(user2, testInstanceId, testKey, user2Value)

                // Values should be isolated per user
                userScopeProvider.currentScope = GlobalContext.get().getScope(user2.id)
                val user2RetrievedValue = scriptPreferenceDataSource.getString(user2, testInstanceId, testKey).first()
                userScopeProvider.currentScope = GlobalContext.get().getScope(testUser.id)
                val user1RetrievedValue =
                    scriptPreferenceDataSource.getString(testUser, testInstanceId, testKey).first()

                assertEquals(user1Value, user1RetrievedValue)
                assertEquals(user2Value, user2RetrievedValue)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test delete preference value - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test delete preference value`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val testKey = "delete_test_key"
                val testValue = "value_to_delete"

                // Set a preference value
                scriptPreferenceDataSource.setString(testUser, testInstanceId, testKey, testValue)
                val initialValue = scriptPreferenceDataSource.getString(testUser, testInstanceId, testKey).first()
                assertEquals(testValue, initialValue)

                // Delete the preference value
                scriptPreferenceDataSource.deleteValue(testUser, testInstanceId, testKey)
                val deletedValue = scriptPreferenceDataSource.getString(testUser, testInstanceId, testKey).first()
                assertNull(deletedValue)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test non-existent preference returns null - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test non-existent preference returns null`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val nonExistentKey = "non_existent_preference"

                // Test all data types return null for non-existent preferences
                assertNull(scriptPreferenceDataSource.getString(testUser, testInstanceId, nonExistentKey).first())
                assertNull(scriptPreferenceDataSource.getInt(testUser, testInstanceId, nonExistentKey).first())
                assertNull(scriptPreferenceDataSource.getLong(testUser, testInstanceId, nonExistentKey).first())
                assertNull(scriptPreferenceDataSource.getFloat(testUser, testInstanceId, nonExistentKey).first())
                assertNull(scriptPreferenceDataSource.getBoolean(testUser, testInstanceId, nonExistentKey).first())
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test multiple preferences with different data types for same package - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test multiple preferences with different data types for same package`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val stringKey = "multi_string_preference"
                val intKey = "multi_int_preference"
                val booleanKey = "multi_boolean_preference"

                val stringValue = "multi_string_value"
                val intValue = 999
                val booleanValue = true

                // Set different types of preferences for the same instance
                scriptPreferenceDataSource.setString(testUser, testInstanceId, stringKey, stringValue)
                scriptPreferenceDataSource.setInt(testUser, testInstanceId, intKey, intValue)
                scriptPreferenceDataSource.setBoolean(testUser, testInstanceId, booleanKey, booleanValue)

                // Retrieve and verify all values
                assertEquals(
                    stringValue,
                    scriptPreferenceDataSource.getString(testUser, testInstanceId, stringKey).first(),
                )
                assertEquals(intValue, scriptPreferenceDataSource.getInt(testUser, testInstanceId, intKey).first())
                assertEquals(
                    booleanValue,
                    scriptPreferenceDataSource.getBoolean(testUser, testInstanceId, booleanKey).first(),
                )
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test overwriting existing preference with different data type - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test overwriting existing preference with different data type`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val testKey = "overwrite_test_preference"

                // Set as string first
                scriptPreferenceDataSource.setString(testUser, testInstanceId, testKey, "string_value")
                assertEquals(
                    "string_value",
                    scriptPreferenceDataSource.getString(testUser, testInstanceId, testKey).first(),
                )

                // Overwrite with integer (stored as string internally)
                scriptPreferenceDataSource.setInt(testUser, testInstanceId, testKey, 42)
                assertEquals(42, scriptPreferenceDataSource.getInt(testUser, testInstanceId, testKey).first())

                // The string representation should now be "42"
                assertEquals("42", scriptPreferenceDataSource.getString(testUser, testInstanceId, testKey).first())
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
                scriptPreferenceDataSource.setString(testUser, testInstanceId, "empty_string", "")
                assertEquals("", scriptPreferenceDataSource.getString(testUser, testInstanceId, "empty_string").first())

                // Test zero values
                scriptPreferenceDataSource.setInt(testUser, testInstanceId, "zero_int", 0)
                assertEquals(0, scriptPreferenceDataSource.getInt(testUser, testInstanceId, "zero_int").first())

                scriptPreferenceDataSource.setLong(testUser, testInstanceId, "zero_long", 0L)
                assertEquals(0L, scriptPreferenceDataSource.getLong(testUser, testInstanceId, "zero_long").first())

                scriptPreferenceDataSource.setFloat(testUser, testInstanceId, "zero_float", 0.0f)
                assertEquals(0.0f, scriptPreferenceDataSource.getFloat(testUser, testInstanceId, "zero_float").first())

                // Test false boolean
                scriptPreferenceDataSource.setBoolean(testUser, testInstanceId, "false_boolean", false)
                assertEquals(
                    false,
                    scriptPreferenceDataSource.getBoolean(testUser, testInstanceId, "false_boolean").first(),
                )
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
                scriptPreferenceDataSource.setString(testUser, testInstanceId, "large_string", largeString)
                assertEquals(
                    largeString,
                    scriptPreferenceDataSource.getString(testUser, testInstanceId, "large_string").first(),
                )

                // Test max integer value
                scriptPreferenceDataSource.setInt(testUser, testInstanceId, "max_int", Int.MAX_VALUE)
                assertEquals(
                    Int.MAX_VALUE,
                    scriptPreferenceDataSource.getInt(testUser, testInstanceId, "max_int").first(),
                )

                // Test max long value
                scriptPreferenceDataSource.setLong(testUser, testInstanceId, "max_long", Long.MAX_VALUE)
                assertEquals(
                    Long.MAX_VALUE,
                    scriptPreferenceDataSource.getLong(testUser, testInstanceId, "max_long").first(),
                )
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test complex instance IDs - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test complex instance IDs`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val complexInstanceId =
                    "complex-instance-id-with-many-dashes-and-uuid-12345678-1234-1234-1234-123456789012"
                val testKey = "complex_instance_test"
                val testValue = "complex_instance_value"

                // Test with complex instance ID
                scriptPreferenceDataSource.setString(testUser, complexInstanceId, testKey, testValue)
                val retrievedValue = scriptPreferenceDataSource.getString(testUser, complexInstanceId, testKey).first()
                assertEquals(testValue, retrievedValue)

                // Ensure it doesn't interfere with other instances
                val simpleRetrievedValue =
                    scriptPreferenceDataSource.getString(testUser, testInstanceId, testKey).first()
                assertNull(simpleRetrievedValue)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test special characters in keys and values - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test special characters in keys and values`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val specialKey = "key_with_special_chars_!@#$%^&*()"
                val specialValue = "value with spaces and special chars: !@#$%^&*()_+-=[]{}|;':\",./<>?"

                // Test with special characters
                scriptPreferenceDataSource.setString(testUser, testInstanceId, specialKey, specialValue)
                val retrievedValue = scriptPreferenceDataSource.getString(testUser, testInstanceId, specialKey).first()
                assertEquals(specialValue, retrievedValue)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test delete specific preference without affecting others - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test delete specific preference without affecting others`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val key1 = "preference_1"
                val key2 = "preference_2"
                val value1 = "value_1"
                val value2 = "value_2"

                // Set multiple preferences
                scriptPreferenceDataSource.setString(testUser, testInstanceId, key1, value1)
                scriptPreferenceDataSource.setString(testUser, testInstanceId, key2, value2)

                // Verify both are set
                assertEquals(value1, scriptPreferenceDataSource.getString(testUser, testInstanceId, key1).first())
                assertEquals(value2, scriptPreferenceDataSource.getString(testUser, testInstanceId, key2).first())

                // Delete only one preference
                scriptPreferenceDataSource.deleteValue(testUser, testInstanceId, key1)

                // Verify only the deleted one is null, the other remains
                assertNull(scriptPreferenceDataSource.getString(testUser, testInstanceId, key1).first())
                assertEquals(value2, scriptPreferenceDataSource.getString(testUser, testInstanceId, key2).first())
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    /**
     * Helper method to create a second test user for isolation tests
     */
    private fun createSecondTestUser(): User =
        User(
            id = "test-user-id-2",
            name = "Test User 2",
            email = "test2@example.com",
            encryptionKey = "12345678901234567890123456789012", // 32 chars
            accessToken = "fake-access-token-for-testing-2",
        )
}

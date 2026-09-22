package com.cereal.client.infrastructure.data.datasource.database.integration

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.toConfigValue
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.DatabaseImplementation
import com.cereal.client.infrastructure.data.datasource.database.DatasetDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.DatabaseConnector
import com.cereal.client.infrastructure.data.datasource.database.room.RoomDatabases
import com.cereal.client.infrastructure.data.datasource.database.room.RoomDatasetDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.UserRoomDatabase
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.DatasetMapper
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
 * Parameterized integration test for DatasetDataSource implementations.
 */
class DatasetDataSourceIntegrationTest {
    private lateinit var datasetDataSource: DatasetDataSource
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

                val datasetMapper = DatasetMapper()
                datasetDataSource = RoomDatasetDataSource(roomDatabases, datasetMapper)
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

    @AfterEach
    fun tearDown() {
        if (::roomDatabases.isInitialized) {
            roomDatabases.closeAll()
        }

        // Clean up temporary directory
        if (::tempDir.isInitialized) {
            tempDir.deleteRecursively()
        }

        // Clean up mocks
        unmockkObject(Encryption)

        // Stop Koin if it was started
        try {
            stopKoin()
        } catch (_: Exception) {
            // Ignore if Koin was not started
        }
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

    private fun createTestItemDefinitions(): List<ScriptConfigurationItemDefinition> =
        listOf(
            ScriptConfigurationItemDefinition(
                name = "Username",
                description = "User login name",
                key = "username",
                position = 1,
                type = ConfigItemType.StringConfigItem,
                isNullable = false,
                stateModifier = null,
                isScriptIdentifier = false,
            ),
            ScriptConfigurationItemDefinition(
                name = "Email",
                description = "User email address",
                key = "email",
                position = 2,
                type = ConfigItemType.StringConfigItem,
                isNullable = true,
                stateModifier = null,
                isScriptIdentifier = false,
            ),
            ScriptConfigurationItemDefinition(
                name = "Age",
                description = "User age",
                key = "age",
                position = 3,
                type = ConfigItemType.IntConfigItem,
                isNullable = true,
                stateModifier = null,
                isScriptIdentifier = false,
            ),
        )

    private fun createTestDatasetItem(): CustomDatasetItem =
        CustomDatasetItem(
            id = UUID.randomUUID(),
            fields =
                mapOf(
                    "username" to "testuser",
                    "email" to "test@example.com",
                    "age" to 25,
                ).mapValues { (_, value) -> value.toConfigValue() },
        )

    @ParameterizedTest(name = "test create and retrieve dataset group - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test create and retrieve dataset group`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val itemDefinitions = createTestItemDefinitions()
                val testDatasetItem = createTestDatasetItem()
                val datasetGroup =
                    CustomDatasetGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Dataset Group",
                        numberOfItems = 1,
                        itemDefinitions = itemDefinitions,
                        items = sequenceOf(testDatasetItem),
                        createdAt = kotlin.time.Instant.fromEpochSeconds(0),
                    )

                // When
                datasetDataSource.createDatasetGroup(testUser, datasetGroup)

                // Then
                val retrievedGroups = datasetDataSource.getDatasetGroups(testUser).first()
                assertEquals(1, retrievedGroups.size)

                val retrievedGroup = retrievedGroups.first()
                assertEquals(datasetGroup.id, retrievedGroup.id)
                assertEquals(datasetGroup.name, retrievedGroup.name)
                assertEquals(itemDefinitions.size, retrievedGroup.itemDefinitions.size)
            } finally {
                tearDown()
            }
        }

    @ParameterizedTest(name = "test update dataset group - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test update dataset group`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val itemDefinitions = createTestItemDefinitions()
                val datasetGroup =
                    CustomDatasetGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Original Name",
                        numberOfItems = 0,
                        itemDefinitions = itemDefinitions,
                        items = emptySequence(),
                        createdAt = kotlin.time.Instant.fromEpochSeconds(0),
                    )
                datasetDataSource.createDatasetGroup(testUser, datasetGroup)

                // When
                val updatedGroup = datasetGroup.copy(name = "Updated Name")
                datasetDataSource.updateDatasetGroup(testUser, updatedGroup)

                // Then
                val retrievedGroup = datasetDataSource.getDatasetGroup(testUser, datasetGroup.id)
                assertNotNull(retrievedGroup)
                assertEquals("Updated Name", retrievedGroup!!.name)
            } finally {
                tearDown()
            }
        }

    @ParameterizedTest(name = "test delete dataset group - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test delete dataset group`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val itemDefinitions = createTestItemDefinitions()
                val datasetGroup =
                    CustomDatasetGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        itemDefinitions = itemDefinitions,
                        items = emptySequence(),
                        createdAt = kotlin.time.Instant.fromEpochSeconds(0),
                    )
                datasetDataSource.createDatasetGroup(testUser, datasetGroup)

                // When
                datasetDataSource.deleteDatasetGroup(testUser, datasetGroup.id)

                // Then
                val retrievedGroups = datasetDataSource.getDatasetGroups(testUser).first()
                assertTrue(retrievedGroups.isEmpty())

                val retrievedGroup = datasetDataSource.getDatasetGroup(testUser, datasetGroup.id)
                assertNull(retrievedGroup)
            } finally {
                tearDown()
            }
        }

    @ParameterizedTest(name = "test create and retrieve dataset item in group - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test create and retrieve dataset item in group`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val itemDefinitions = createTestItemDefinitions()
                val datasetGroup =
                    CustomDatasetGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        itemDefinitions = itemDefinitions,
                        items = emptySequence(),
                        createdAt = kotlin.time.Instant.fromEpochSeconds(0),
                    )
                datasetDataSource.createDatasetGroup(testUser, datasetGroup)

                val testDatasetItem = createTestDatasetItem()

                // When
                datasetDataSource.createOrUpdateDataset(testUser, testDatasetItem, datasetGroup.id)

                // Then
                val datasetsInGroup = datasetDataSource.getDatasetsFromGroup(testUser, datasetGroup.id)
                assertEquals(1, datasetsInGroup.size)

                val retrievedDataset = datasetsInGroup.first()
                assertEquals(testDatasetItem.id, retrievedDataset.id)
                assertEquals(testDatasetItem.fields["username"], retrievedDataset.fields["username"])
                assertEquals(testDatasetItem.fields["email"], retrievedDataset.fields["email"])
                assertEquals(testDatasetItem.fields["age"], retrievedDataset.fields["age"])
            } finally {
                tearDown()
            }
        }

    @ParameterizedTest(name = "test get datasets count in group - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test get datasets count in group`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val itemDefinitions = createTestItemDefinitions()
                val datasetGroup =
                    CustomDatasetGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        itemDefinitions = itemDefinitions,
                        items = emptySequence(),
                        createdAt = kotlin.time.Instant.fromEpochSeconds(0),
                    )
                datasetDataSource.createDatasetGroup(testUser, datasetGroup)

                val dataset1 =
                    CustomDatasetItem(UUID.randomUUID(), mapOf("username" to "user1", "email" to "user1@test.com").mapValues { (_, value) -> value.toConfigValue() })
                val dataset2 =
                    CustomDatasetItem(UUID.randomUUID(), mapOf("username" to "user2", "email" to "user2@test.com").mapValues { (_, value) -> value.toConfigValue() })

                // When
                datasetDataSource.createOrUpdateDataset(testUser, dataset1, datasetGroup.id)
                datasetDataSource.createOrUpdateDataset(testUser, dataset2, datasetGroup.id)

                // Then
                val count = datasetDataSource.getDatasetsInGroupCount(testUser, datasetGroup.id)
                assertEquals(2, count)
            } finally {
                tearDown()
            }
        }

    @ParameterizedTest(name = "test get datasets flow - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test get datasets flow`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val itemDefinitions = createTestItemDefinitions()
                val datasetGroup =
                    CustomDatasetGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        itemDefinitions = itemDefinitions,
                        items = emptySequence(),
                        createdAt = kotlin.time.Instant.fromEpochSeconds(0),
                    )
                datasetDataSource.createDatasetGroup(testUser, datasetGroup)

                val testDatasetItem = createTestDatasetItem()

                // When
                datasetDataSource.createOrUpdateDataset(testUser, testDatasetItem, datasetGroup.id)

                // Then
                val datasetsFlow = datasetDataSource.getDatasetsFlow(testUser, datasetGroup.id).first()
                assertEquals(1, datasetsFlow.size)
                assertEquals(testDatasetItem.id, datasetsFlow.first().id)
            } finally {
                tearDown()
            }
        }

    @ParameterizedTest(name = "test get specific dataset by id - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test get specific dataset by id`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val itemDefinitions = createTestItemDefinitions()
                val datasetGroup =
                    CustomDatasetGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        itemDefinitions = itemDefinitions,
                        items = emptySequence(),
                        createdAt = kotlin.time.Instant.fromEpochSeconds(0),
                    )
                datasetDataSource.createDatasetGroup(testUser, datasetGroup)

                val testDatasetItem = createTestDatasetItem()
                datasetDataSource.createOrUpdateDataset(testUser, testDatasetItem, datasetGroup.id)

                // When
                val retrievedDataset = datasetDataSource.getDataset(testUser, testDatasetItem.id.toString())

                // Then
                assertNotNull(retrievedDataset)
                assertEquals(testDatasetItem.id, retrievedDataset!!.id)
                assertEquals(testDatasetItem.fields["username"], retrievedDataset.fields["username"])
            } finally {
                tearDown()
            }
        }

    @ParameterizedTest(name = "test update existing dataset - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test update existing dataset`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val itemDefinitions = createTestItemDefinitions()
                val datasetGroup =
                    CustomDatasetGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        itemDefinitions = itemDefinitions,
                        items = emptySequence(),
                        createdAt = kotlin.time.Instant.fromEpochSeconds(0),
                    )
                datasetDataSource.createDatasetGroup(testUser, datasetGroup)

                val originalDataset = createTestDatasetItem()
                datasetDataSource.createOrUpdateDataset(testUser, originalDataset, datasetGroup.id)

                // When
                val updatedDataset =
                    originalDataset.copy(
                        fields =
                            mapOf(
                                "username" to "updated_user",
                                "email" to "updated@test.com",
                                "age" to 30,
                            ).mapValues { (_, value) -> value.toConfigValue() },
                    )
                datasetDataSource.createOrUpdateDataset(testUser, updatedDataset, datasetGroup.id)

                // Then
                val retrievedDataset = datasetDataSource.getDataset(testUser, originalDataset.id.toString())
                assertNotNull(retrievedDataset)
                assertEquals("updated_user", retrievedDataset!!.fields["username"]?.raw)
                assertEquals("updated@test.com", retrievedDataset.fields["email"]?.raw)
                assertEquals(30, retrievedDataset.fields["age"]?.raw)

                // Should still be only one dataset in the group
                val datasetsCount = datasetDataSource.getDatasetsInGroupCount(testUser, datasetGroup.id)
                assertEquals(1, datasetsCount)
            } finally {
                tearDown()
            }
        }

    @ParameterizedTest(name = "test delete dataset - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test delete dataset`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val itemDefinitions = createTestItemDefinitions()
                val datasetGroup =
                    CustomDatasetGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        itemDefinitions = itemDefinitions,
                        items = emptySequence(),
                        createdAt = kotlin.time.Instant.fromEpochSeconds(0),
                    )
                datasetDataSource.createDatasetGroup(testUser, datasetGroup)

                val testDatasetItem = createTestDatasetItem()
                datasetDataSource.createOrUpdateDataset(testUser, testDatasetItem, datasetGroup.id)

                // When
                datasetDataSource.deleteDataset(testUser, testDatasetItem.id)

                // Then
                val retrievedDataset = datasetDataSource.getDataset(testUser, testDatasetItem.id.toString())
                assertNull(retrievedDataset)

                val datasetsCount = datasetDataSource.getDatasetsInGroupCount(testUser, datasetGroup.id)
                assertEquals(0, datasetsCount)
            } finally {
                tearDown()
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
                val itemDefinitions = createTestItemDefinitions()
                val datasetGroup =
                    CustomDatasetGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        itemDefinitions = itemDefinitions,
                        items = emptySequence(),
                        createdAt = kotlin.time.Instant.fromEpochSeconds(0),
                    )

                // When
                datasetDataSource.createDatasetGroup(testUser, datasetGroup)

                // Then
                userScopeProvider.currentScope = GlobalContext.get().getScope(testUser.id)
                val user1Groups = datasetDataSource.getDatasetGroups(testUser).first()
                userScopeProvider.currentScope = GlobalContext.get().getScope(user2.id)
                val user2Groups = datasetDataSource.getDatasetGroups(user2).first()

                assertEquals(1, user1Groups.size)
                assertEquals(0, user2Groups.size)
            } finally {
                tearDown()
            }
        }

    @ParameterizedTest(name = "test get dataset group by id - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test get dataset group by id`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val itemDefinitions = createTestItemDefinitions()
                val datasetGroup =
                    CustomDatasetGroup(
                        id = UUID.randomUUID().toString(),
                        name = "Test Group",
                        numberOfItems = 0,
                        itemDefinitions = itemDefinitions,
                        items = emptySequence(),
                        createdAt = kotlin.time.Instant.fromEpochSeconds(0),
                    )
                datasetDataSource.createDatasetGroup(testUser, datasetGroup)

                // When
                val retrievedGroup = datasetDataSource.getDatasetGroup(testUser, datasetGroup.id)

                // Then
                assertNotNull(retrievedGroup)
                assertEquals(datasetGroup.id, retrievedGroup!!.id)
                assertEquals(datasetGroup.name, retrievedGroup.name)
                assertEquals(itemDefinitions.size, retrievedGroup.itemDefinitions.size)
            } finally {
                tearDown()
            }
        }
}

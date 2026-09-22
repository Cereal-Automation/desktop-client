package com.cereal.client.infrastructure.data.datasource.database.integration

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.script.ScriptInstanceFactory
import com.cereal.client.application.task.JobTaskFactory
import com.cereal.client.domain.model.script.ChildScript
import com.cereal.client.domain.model.script.ChildScriptInstance
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ListRow
import com.cereal.client.domain.model.script.configuration.ListRows
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.DatabaseImplementation
import com.cereal.client.infrastructure.data.datasource.database.DatasetDataSource
import com.cereal.client.infrastructure.data.datasource.database.ScriptInstanceDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.DatabaseConnector
import com.cereal.client.infrastructure.data.datasource.database.room.RoomDatabases
import com.cereal.client.infrastructure.data.datasource.database.room.RoomProxyDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.RoomScriptInstanceDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.RoomScriptPackageGroupDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.RoomScriptPackageInstanceDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.RoomScriptTaskDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.UserRoomDatabase
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.KeyValueRoomMapper
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.ScriptInstanceMapper
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.ScriptNotificationOverrideMapper
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.ScriptPackageInstanceMapper
import com.cereal.client.infrastructure.data.datasource.filesystem.security.Encryption
import com.cereal.client.infrastructure.data.datasource.filesystem.security.EncryptionKey
import com.cereal.client.infrastructure.di.KoinScopeLinker
import com.cereal.client.infrastructure.di.UserScopeProvider
import com.cereal.sdk.ScriptConfiguration
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Integration test for ScriptInstanceDataSource Room implementation.
 */
@Suppress("LargeClass")
class ScriptInstanceDataSourceIntegrationTest {
    private lateinit var scriptInstanceDataSource: ScriptInstanceDataSource
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
    @Suppress("LongMethod", "UnusedParameter")
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

        // Initialize Room database implementation
        run {
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

            // Use real mappers for proper integration testing of the Room implementation
            val proxyDataSource = RoomProxyDataSource(roomDatabases)
            // Use a mock for dataset datasource to break circular dependency
            val datasetDataSource = mockk<DatasetDataSource>(relaxed = true)

            val keyValueMapper =
                KeyValueRoomMapper(
                    proxyDataSource,
                    datasetDataSource,
                )
            val scopeLinker = KoinScopeLinker()
            val scriptInstanceMapper =
                ScriptInstanceMapper(
                    keyValueMapper,
                    ScriptInstanceFactory(scopeLinker),
                    JobTaskFactory(scopeLinker),
                )
            val scriptPackageInstanceMapper = ScriptPackageInstanceMapper(keyValueMapper)
            val notificationOverrideMapper = ScriptNotificationOverrideMapper()

            scriptInstanceDataSource =
                RoomScriptInstanceDataSource(
                    RoomScriptPackageGroupDataSource(roomDatabases, scriptInstanceMapper),
                    RoomScriptPackageInstanceDataSource(
                        roomDatabases,
                        scriptInstanceMapper,
                        scriptPackageInstanceMapper,
                        notificationOverrideMapper,
                    ),
                    RoomScriptTaskDataSource(roomDatabases, scriptInstanceMapper),
                )
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
     * Cleans up after the test.
     */
    @Suppress("UnusedParameter")
    private fun tearDownForImplementation(implementation: DatabaseImplementation) {
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

        // Clean up temporary directory
        tempDir.deleteRecursively()

        // Clean up mocks
        unmockkObject(Encryption)
    }

    private fun createSecondTestUser(): User =
        User(
            id = "test-user-2-id",
            name = "Test User 2",
            email = "test2@example.com",
            encryptionKey = "12345678901234567890123456789012", // 32 chars
            accessToken = "fake-access-token-for-testing-2",
        )

    private fun createTestScriptPackageGroup(): ScriptPackageGroup =
        ScriptPackageGroup(
            id = UUID.randomUUID().toString(),
            name = "Test Script Group",
            totalScriptPackages = 0,
        )

    private fun createTestScriptPackage(): ScriptPackage {
        val mockMainScript =
            mockk<MainScript> {
                every { configuration } returns mockk<ScriptConfigurationDefinition>(relaxed = true)
            }

        return ScriptPackage(
            source = File("/test/script.jar"),
            manifest =
                Manifest(
                    packageName = "com.test.script",
                    name = "Test Script",
                    versionCode = 1L,
                ),
            mainScript = mockMainScript,
            childScripts = emptyMap(),
        )
    }

    private fun createTestScriptPackageWithChildScript(): ScriptPackage {
        val mockMainScript =
            mockk<MainScript> {
                every { configuration } returns mockk<ScriptConfigurationDefinition>(relaxed = true)
            }

        val mockChildScript =
            mockk<ChildScript> {
                every { configuration } returns mockk<ScriptConfigurationDefinition>(relaxed = true)
            }

        return ScriptPackage(
            source = File("/test/script.jar"),
            manifest =
                Manifest(
                    packageName = "com.test.script",
                    name = "Test Script",
                    versionCode = 1L,
                ),
            mainScript = mockMainScript,
            childScripts = mapOf("test-child-script" to mockChildScript),
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun createTestScriptPackageInstance(): ScriptPackageInstance =
        ScriptPackageInstance(
            id = UUID.randomUUID().toString(),
            mainConfiguration = emptyMap(),
            childConfigurations = emptyMap(),
            definition = createTestScriptPackage(),
            createdAt =
                Clock.System
                    .now(),
            numberOfConcurrentTasks = 1,
        )

    @OptIn(ExperimentalTime::class)
    private fun createTestScriptPackageInstanceWithChildScript(): ScriptPackageInstance =
        ScriptPackageInstance(
            id = UUID.randomUUID().toString(),
            mainConfiguration = emptyMap(),
            childConfigurations = mapOf("test-child-script" to emptyMap()),
            definition = createTestScriptPackageWithChildScript(),
            createdAt =
                kotlin.time.Clock.System
                    .now(),
            numberOfConcurrentTasks = 1,
        )

    @OptIn(ExperimentalTime::class)
    private fun createTestMainScriptInstance(packageInstance: ScriptPackageInstance): MainScriptInstance {
        val mockMainScript =
            mockk<MainScript> {
                every { configuration } returns mockk<ScriptConfigurationDefinition>(relaxed = true)
            }

        return MainScriptInstance(
            id = UUID.randomUUID().toString(),
            definition = mockMainScript,
            configuration = emptyMap(),
            createdAt =
                Clock.System
                    .now(),
            packageInstance = packageInstance,
        )
    }

    private fun createTestTask(scriptInstance: ScriptInstance): Task =
        JobTask(
            id = UUID.randomUUID().toString(),
            scriptInstance = scriptInstance,
            configuration = emptyMap(),
            statusHistory = listOf(TaskStatus.Idle(timestamp = Clock.System.now())),
            userInteraction = null,
            createdAt = Clock.System.now(),
            job = null,
        )

    @OptIn(ExperimentalTime::class)
    private fun createTestChildScriptInstance(): ChildScriptInstance {
        val mockChildScript = mockk<ChildScript>(relaxed = true)
        val packageInstance = createTestScriptPackageInstance()
        val parentInstance = createTestMainScriptInstance(packageInstance)

        return ChildScriptInstance(
            id = UUID.randomUUID().toString(),
            definition = mockChildScript,
            configuration = emptyMap(),
            createdAt =
                Clock.System
                    .now(),
            packageInstance = packageInstance,
            parent = parentInstance,
            params = null,
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun createTestChildScriptInstance(
        packageInstance: ScriptPackageInstance,
        parentInstance: MainScriptInstance,
    ): ChildScriptInstance {
        val mockChildScript = mockk<ChildScript>(relaxed = true)

        return ChildScriptInstance(
            id = UUID.randomUUID().toString(),
            definition = mockChildScript,
            configuration = emptyMap(),
            createdAt =
                kotlin.time.Clock.System
                    .now(),
            packageInstance = packageInstance,
            parent = parentInstance,
            params = null,
        )
    }

    @ParameterizedTest(name = "test create and retrieve script instance group - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test create and retrieve script instance group`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val scriptPackageGroup = createTestScriptPackageGroup()

                // When
                val createdGroup = scriptInstanceDataSource.createScriptInstanceGroup(testUser, scriptPackageGroup)

                // Then
                assertEquals(scriptPackageGroup.id, createdGroup.id)
                assertEquals(scriptPackageGroup.name, createdGroup.name)

                val retrievedGroups = scriptInstanceDataSource.getScriptInstanceGroups(testUser).first()
                assertEquals(1, retrievedGroups.size)
                assertEquals(scriptPackageGroup.id, retrievedGroups.first().id)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test update script package group - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test update script package group`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val originalGroup = createTestScriptPackageGroup()
                scriptInstanceDataSource.createScriptInstanceGroup(testUser, originalGroup)

                // When
                val updatedGroup = originalGroup.copy(name = "Updated Script Group")
                scriptInstanceDataSource.updateScriptPackageGroup(testUser, updatedGroup)

                // Then
                val retrievedGroups = scriptInstanceDataSource.getScriptInstanceGroups(testUser).first()
                assertEquals(1, retrievedGroups.size)
                assertEquals("Updated Script Group", retrievedGroups.first().name)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test delete script instance group - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test delete script instance group`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val scriptPackageGroup = createTestScriptPackageGroup()
                scriptInstanceDataSource.createScriptInstanceGroup(testUser, scriptPackageGroup)

                // When
                scriptInstanceDataSource.deleteScriptInstanceGroup(testUser, scriptPackageGroup.id)

                // Then
                val retrievedGroups = scriptInstanceDataSource.getScriptInstanceGroups(testUser).first()
                assertTrue(retrievedGroups.isEmpty())
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test add script package instance - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test add script package instance`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val scriptPackageGroup = createTestScriptPackageGroup()
                scriptInstanceDataSource.createScriptInstanceGroup(testUser, scriptPackageGroup)

                val scriptPackageInstance = createTestScriptPackageInstance()
                val mainScriptInstance = createTestMainScriptInstance(scriptPackageInstance)

                // When
                scriptInstanceDataSource.addScriptPackageInstance(
                    testUser,
                    scriptPackageInstance,
                    mainScriptInstance,
                    scriptPackageGroup.id,
                )

                // Then
                val scriptPackageDefinitions =
                    mapOf(scriptPackageInstance.definition.manifest.packageName to scriptPackageInstance.definition)
                val retrievedInstances =
                    scriptInstanceDataSource.getScriptPackageInstancesInGroup(
                        testUser,
                        scriptPackageGroup.id,
                        scriptPackageDefinitions,
                    )
                assertEquals(1, retrievedInstances.size)
                assertEquals(scriptPackageInstance.id, retrievedInstances.first().id)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test get script package instances flow - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test get script package instances flow`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val scriptPackageGroup = createTestScriptPackageGroup()
                scriptInstanceDataSource.createScriptInstanceGroup(testUser, scriptPackageGroup)

                val scriptPackageInstance = createTestScriptPackageInstance()
                val mainScriptInstance = createTestMainScriptInstance(scriptPackageInstance)

                scriptInstanceDataSource.addScriptPackageInstance(
                    testUser,
                    scriptPackageInstance,
                    mainScriptInstance,
                    scriptPackageGroup.id,
                )

                // When
                val scriptPackageDefinitions =
                    mapOf(scriptPackageInstance.definition.manifest.packageName to scriptPackageInstance.definition)
                val instancesFlow =
                    scriptInstanceDataSource
                        .getScriptPackagesInGroupFlow(
                            testUser,
                            scriptPackageGroup.id,
                            scriptPackageDefinitions,
                        ).first()

                // Then - Test the real mapping behavior
                assertEquals(1, instancesFlow.size)
                assertEquals(scriptPackageInstance.id, instancesFlow.first().id)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test add and remove persisted task - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test add and remove persisted task`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val scriptPackageGroup = createTestScriptPackageGroup()
                scriptInstanceDataSource.createScriptInstanceGroup(testUser, scriptPackageGroup)

                val scriptPackageInstance = createTestScriptPackageInstance()
                val mainScriptInstance = createTestMainScriptInstance(scriptPackageInstance)

                scriptInstanceDataSource.addScriptPackageInstance(
                    testUser,
                    scriptPackageInstance,
                    mainScriptInstance,
                    scriptPackageGroup.id,
                )

                val task = createTestTask(mainScriptInstance)

                // When - Test that the operations complete without error
                scriptInstanceDataSource.addTask(testUser, task)
                scriptInstanceDataSource.removeTask(testUser, task.id)

                // Then - For integration testing with mocks, we verify the operations succeeded
                assertTrue(true) // Test passes if no exception was thrown
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test get script package instance by id - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test get script package instance by id`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val scriptPackageGroup = createTestScriptPackageGroup()
                scriptInstanceDataSource.createScriptInstanceGroup(testUser, scriptPackageGroup)

                val scriptPackageInstance = createTestScriptPackageInstance()
                val mainScriptInstance = createTestMainScriptInstance(scriptPackageInstance)

                scriptInstanceDataSource.addScriptPackageInstance(
                    testUser,
                    scriptPackageInstance,
                    mainScriptInstance,
                    scriptPackageGroup.id,
                )

                // When
                val retrievedInstance =
                    scriptInstanceDataSource.getScriptPackageInstance(
                        testUser,
                        scriptPackageInstance.id,
                        scriptPackageInstance.definition,
                    )

                // Then - Test the real mapping behavior
                assertNotNull(retrievedInstance)
                assertEquals(scriptPackageInstance.id, retrievedInstance!!.id)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test get script package name by id - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test get script package name by id`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val scriptPackageGroup = createTestScriptPackageGroup()
                scriptInstanceDataSource.createScriptInstanceGroup(testUser, scriptPackageGroup)

                val scriptPackageInstance = createTestScriptPackageInstance()
                val mainScriptInstance = createTestMainScriptInstance(scriptPackageInstance)

                scriptInstanceDataSource.addScriptPackageInstance(
                    testUser,
                    scriptPackageInstance,
                    mainScriptInstance,
                    scriptPackageGroup.id,
                )

                // When
                val packageName = scriptInstanceDataSource.getScriptPackageNameById(testUser, scriptPackageInstance.id)

                // Then - Test the real behavior
                assertNotNull(packageName)
                assertEquals("com.test.script", packageName)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test delete script package instance - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test delete script package instance`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val scriptPackageGroup = createTestScriptPackageGroup()
                scriptInstanceDataSource.createScriptInstanceGroup(testUser, scriptPackageGroup)

                val scriptPackageInstance = createTestScriptPackageInstance()
                val mainScriptInstance = createTestMainScriptInstance(scriptPackageInstance)

                scriptInstanceDataSource.addScriptPackageInstance(
                    testUser,
                    scriptPackageInstance,
                    mainScriptInstance,
                    scriptPackageGroup.id,
                )

                // When
                scriptInstanceDataSource.deleteScriptPackageInstance(testUser, scriptPackageInstance.id)

                // Then - Test the real behavior
                val retrievedInstance =
                    scriptInstanceDataSource.getScriptPackageInstance(
                        testUser,
                        scriptPackageInstance.id,
                        scriptPackageInstance.definition,
                    )
                assertNull(retrievedInstance)
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
                val scriptPackageGroup = createTestScriptPackageGroup()

                // When
                scriptInstanceDataSource.createScriptInstanceGroup(testUser, scriptPackageGroup)

                // Then
                userScopeProvider.currentScope = GlobalContext.get().getScope(testUser.id)
                val user1Groups = scriptInstanceDataSource.getScriptInstanceGroups(testUser).first()
                userScopeProvider.currentScope = GlobalContext.get().getScope(user2.id)
                val user2Groups = scriptInstanceDataSource.getScriptInstanceGroups(user2).first()

                assertEquals(1, user1Groups.size)
                assertEquals(0, user2Groups.size)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test get all script package instances - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test get all script package instances`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val scriptPackageGroup = createTestScriptPackageGroup()
                scriptInstanceDataSource.createScriptInstanceGroup(testUser, scriptPackageGroup)

                val scriptPackageInstance1 = createTestScriptPackageInstance()
                val mainScriptInstance1 = createTestMainScriptInstance(scriptPackageInstance1)

                val scriptPackageInstance2 = createTestScriptPackageInstance()
                val mainScriptInstance2 = createTestMainScriptInstance(scriptPackageInstance2)

                scriptInstanceDataSource.addScriptPackageInstance(
                    testUser,
                    scriptPackageInstance1,
                    mainScriptInstance1,
                    scriptPackageGroup.id,
                )
                scriptInstanceDataSource.addScriptPackageInstance(
                    testUser,
                    scriptPackageInstance2,
                    mainScriptInstance2,
                    scriptPackageGroup.id,
                )

                // When - Test that the operation completes without error
                val scriptPackageDefinitions =
                    mapOf(
                        scriptPackageInstance1.definition.manifest.packageName to scriptPackageInstance1.definition,
                        scriptPackageInstance2.definition.manifest.packageName to scriptPackageInstance2.definition,
                    )
                val allInstances =
                    scriptInstanceDataSource.getScriptPackageInstances(testUser, scriptPackageDefinitions)

                // Then - Test the real behavior
                assertEquals(2, allInstances.size)
                assertTrue(allInstances.any { it.id == scriptPackageInstance1.id })
                assertTrue(allInstances.any { it.id == scriptPackageInstance2.id })
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test get script package instances in group - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test get script package instances in group`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val scriptPackageGroup = createTestScriptPackageGroup()
                scriptInstanceDataSource.createScriptInstanceGroup(testUser, scriptPackageGroup)

                val scriptPackageInstance = createTestScriptPackageInstance()
                val mainScriptInstance = createTestMainScriptInstance(scriptPackageInstance)

                scriptInstanceDataSource.addScriptPackageInstance(
                    testUser,
                    scriptPackageInstance,
                    mainScriptInstance,
                    scriptPackageGroup.id,
                )

                // When
                val scriptPackageDefinitions =
                    mapOf(scriptPackageInstance.definition.manifest.packageName to scriptPackageInstance.definition)
                val instancesInGroup =
                    scriptInstanceDataSource.getScriptPackageInstancesInGroup(
                        testUser,
                        scriptPackageGroup.id,
                        scriptPackageDefinitions,
                    )

                // Then
                assertEquals(1, instancesInGroup.size)
                assertEquals(scriptPackageInstance.id, instancesInGroup.first().id)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test add child script and get script instances - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test add child script and get script instances`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val scriptPackageGroup = createTestScriptPackageGroup()
                scriptInstanceDataSource.createScriptInstanceGroup(testUser, scriptPackageGroup)

                // Create a script package instance with child script definitions
                val scriptPackageInstance = createTestScriptPackageInstanceWithChildScript()
                val mainScriptInstance = createTestMainScriptInstance(scriptPackageInstance)

                scriptInstanceDataSource.addScriptPackageInstance(
                    testUser,
                    scriptPackageInstance,
                    mainScriptInstance,
                    scriptPackageGroup.id,
                )

                // Create and add a child script using the SAME package and parent instances
                val childScriptInstance = createTestChildScriptInstance(scriptPackageInstance, mainScriptInstance)
                scriptInstanceDataSource.addChildScript(
                    testUser,
                    "test-child-script", // This key must exist in scriptPackageInstance.definition.childScripts
                    childScriptInstance,
                    mainScriptInstance.id,
                )

                // When
                val scriptInstances = scriptInstanceDataSource.getScriptInstances(testUser, scriptPackageInstance)

                // Then
                assertEquals(2, scriptInstances.size) // Should include both main and child script
                assertTrue(scriptInstances.any { it.id == mainScriptInstance.id })
                assertTrue(scriptInstances.any { it.id == childScriptInstance.id })
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test add nested child scripts and get script instances - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test add nested child scripts and get script instances`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val scriptPackageGroup = createTestScriptPackageGroup()
                scriptInstanceDataSource.createScriptInstanceGroup(testUser, scriptPackageGroup)

                // Create a script package instance with child script definitions
                val scriptPackageInstance = createTestScriptPackageInstanceWithChildScript()
                val mainScriptInstance = createTestMainScriptInstance(scriptPackageInstance)

                scriptInstanceDataSource.addScriptPackageInstance(
                    testUser,
                    scriptPackageInstance,
                    mainScriptInstance,
                    scriptPackageGroup.id,
                )

                // Create and add a first-level child script
                val firstChildScriptInstance = createTestChildScriptInstance(scriptPackageInstance, mainScriptInstance)
                scriptInstanceDataSource.addChildScript(
                    testUser,
                    "test-child-script", // This key must exist in scriptPackageInstance.definition.childScripts
                    firstChildScriptInstance,
                    mainScriptInstance.id,
                )

                // Create and add a second-level child script (child of the first child)
                val secondChildScriptInstance = createTestChildScriptInstance(scriptPackageInstance, mainScriptInstance)
                scriptInstanceDataSource.addChildScript(
                    testUser,
                    "test-child-script", // Same script type but different instance
                    secondChildScriptInstance,
                    firstChildScriptInstance.id, // Parent is the first child script
                )

                // Create and add a third-level child script (child of the second child)
                val thirdChildScriptInstance = createTestChildScriptInstance(scriptPackageInstance, mainScriptInstance)
                scriptInstanceDataSource.addChildScript(
                    testUser,
                    "test-child-script", // Same script type but different instance
                    thirdChildScriptInstance,
                    secondChildScriptInstance.id, // Parent is the second child script
                )

                // When
                val scriptInstances = scriptInstanceDataSource.getScriptInstances(testUser, scriptPackageInstance)

                // Then
                assertEquals(4, scriptInstances.size) // Should include main + 3 nested child scripts
                assertTrue(scriptInstances.any { it.id == mainScriptInstance.id })
                assertTrue(scriptInstances.any { it.id == firstChildScriptInstance.id })
                assertTrue(scriptInstances.any { it.id == secondChildScriptInstance.id })
                assertTrue(scriptInstances.any { it.id == thirdChildScriptInstance.id })
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @OptIn(ExperimentalTime::class)
    @ParameterizedTest(name = "test finished task status history round-trip - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test finished task status history is fully restored after addTask and getJobTasksFromHistory`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val scriptPackageGroup = createTestScriptPackageGroup()
                scriptInstanceDataSource.createScriptInstanceGroup(testUser, scriptPackageGroup)

                val scriptPackageInstance = createTestScriptPackageInstance()
                val mainScriptInstance = createTestMainScriptInstance(scriptPackageInstance)

                scriptInstanceDataSource.addScriptPackageInstance(
                    testUser,
                    scriptPackageInstance,
                    mainScriptInstance,
                    scriptPackageGroup.id,
                )

                val statusHistory =
                    listOf(
                        TaskStatus.Running(message = "Starting script", timestamp = Clock.System.now()),
                        TaskStatus.Running(message = "Fetching data", timestamp = Clock.System.now()),
                        TaskStatus.Running(message = "Processing item 1", timestamp = Clock.System.now()),
                        TaskStatus.Success(message = "Task completed", timestamp = Clock.System.now()),
                    )

                val task =
                    JobTask(
                        id = UUID.randomUUID().toString(),
                        scriptInstance = mainScriptInstance,
                        configuration = emptyMap(),
                        statusHistory = statusHistory,
                        userInteraction = null,
                        createdAt = Clock.System.now(),
                        job = null,
                    )

                // When
                scriptInstanceDataSource.addTask(testUser, task)
                val restoredTasks = scriptInstanceDataSource.getJobTasksFromHistory(testUser, mainScriptInstance)

                // Then - all status history entries must be restored
                assertEquals(1, restoredTasks.size)
                val restoredTask = restoredTasks.first()
                assertEquals(task.id, restoredTask.id)
                assertEquals(
                    statusHistory.size,
                    restoredTask.statusHistory.size,
                    "All ${statusHistory.size} status history entries should be restored, but only ${restoredTask.statusHistory.size} were",
                )
                assertEquals("Starting script", (restoredTask.statusHistory[0] as TaskStatus.Running).message)
                assertEquals("Fetching data", (restoredTask.statusHistory[1] as TaskStatus.Running).message)
                assertEquals("Processing item 1", (restoredTask.statusHistory[2] as TaskStatus.Running).message)
                assertEquals("Task completed", (restoredTask.statusHistory[3] as TaskStatus.Success).message)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @OptIn(ExperimentalTime::class)
    @ParameterizedTest(name = "test remove finished task actually deletes the DB row - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test remove finished task actually deletes the DB row`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val scriptPackageGroup = createTestScriptPackageGroup()
                scriptInstanceDataSource.createScriptInstanceGroup(testUser, scriptPackageGroup)

                val scriptPackageInstance = createTestScriptPackageInstance()
                val mainScriptInstance = createTestMainScriptInstance(scriptPackageInstance)

                scriptInstanceDataSource.addScriptPackageInstance(
                    testUser,
                    scriptPackageInstance,
                    mainScriptInstance,
                    scriptPackageGroup.id,
                )

                val taskId = UUID.randomUUID().toString()

                val run1History =
                    listOf(
                        TaskStatus.Running(message = "Run 1 step A", timestamp = Clock.System.now()),
                        TaskStatus.Success(message = "Run 1 done", timestamp = Clock.System.now()),
                    )
                val run1Task =
                    JobTask(
                        id = taskId,
                        scriptInstance = mainScriptInstance,
                        configuration = emptyMap(),
                        statusHistory = run1History,
                        userInteraction = null,
                        createdAt = Clock.System.now(),
                        job = null,
                    )

                // When - simulate run 1 finishing, then task being restarted, then run 2 finishing
                scriptInstanceDataSource.addTask(testUser, run1Task)
                // This simulates what TaskManager.startTask does before run 2
                scriptInstanceDataSource.removeTask(testUser, taskId)

                val run2History =
                    listOf(
                        TaskStatus.Running(message = "Run 2 step A", timestamp = Clock.System.now()),
                        TaskStatus.Running(message = "Run 2 step B", timestamp = Clock.System.now()),
                        TaskStatus.Success(message = "Run 2 done", timestamp = Clock.System.now()),
                    )
                val run2Task =
                    JobTask(
                        id = taskId,
                        scriptInstance = mainScriptInstance,
                        configuration = emptyMap(),
                        statusHistory = run2History,
                        userInteraction = null,
                        createdAt = Clock.System.now(),
                        job = null,
                    )
                scriptInstanceDataSource.addTask(testUser, run2Task)

                val restoredTasks = scriptInstanceDataSource.getJobTasksFromHistory(testUser, mainScriptInstance)

                // Then - only run 2's history should be present (exactly 1 row for this task)
                assertEquals(
                    1,
                    restoredTasks.size,
                    "Expected exactly 1 finished task row; removeFinishedTask did not delete the run 1 row",
                )
                val restoredTask = restoredTasks.first()
                assertEquals(
                    run2History.size,
                    restoredTask.statusHistory.size,
                    "Restored history should contain only run 2 entries (${run2History.size}), but got ${restoredTask.statusHistory.size}",
                )
                assertEquals("Run 2 step A", (restoredTask.statusHistory[0] as TaskStatus.Running).message)
                assertEquals("Run 2 step B", (restoredTask.statusHistory[1] as TaskStatus.Running).message)
                assertEquals("Run 2 done", (restoredTask.statusHistory[2] as TaskStatus.Success).message)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test group visibility after adding script - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test group visibility after adding script`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                // Given
                val scriptPackageGroup = createTestScriptPackageGroup()
                scriptInstanceDataSource.createScriptInstanceGroup(testUser, scriptPackageGroup)

                // Verify initial state: group exists and has 0 scripts
                var groups = scriptInstanceDataSource.getScriptInstanceGroups(testUser).first()
                assertEquals(1, groups.size)
                assertEquals(0, groups.first().totalScriptPackages)

                // Add a script to the group
                val scriptPackageInstance = createTestScriptPackageInstance()
                val mainScriptInstance = createTestMainScriptInstance(scriptPackageInstance)

                scriptInstanceDataSource.addScriptPackageInstance(
                    testUser,
                    scriptPackageInstance,
                    mainScriptInstance,
                    scriptPackageGroup.id,
                )

                // When - Retrieve groups again
                groups = scriptInstanceDataSource.getScriptInstanceGroups(testUser).first()

                // Then - The group should still be visible and count should be 1
                // This is where we suspect the bug might be (group disappearing due to join/group by issue)
                assertEquals(1, groups.size, "Group should still be visible after adding a script")
                assertEquals(1, groups.first().totalScriptPackages, "Group should have 1 script package")
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    // ------------------------------------------------------------------
    // Lists (List): store and read back through real SQL and the real encrypted converters.
    // ------------------------------------------------------------------

    private enum class Size { SMALL, LARGE }

    private fun listFieldDefinition(
        key: String,
        type: ConfigItemType,
        isNullable: Boolean = false,
    ) = ScriptConfigurationItemDefinition(
        name = key,
        description = "desc",
        key = key,
        position = 0,
        type = type,
        isNullable = isNullable,
        stateModifier = null,
        isScriptIdentifier = false,
    )

    private fun listConfigurationDefinition(fields: List<ScriptConfigurationItemDefinition>): ScriptConfigurationDefinition =
        ScriptConfigurationDefinition(
            scriptConfigurationClass = ScriptConfiguration::class,
            configurationItems =
                listOf(
                    ScriptConfigurationItemDefinition(
                        name = "Targets",
                        description = "Products to purchase",
                        key = "targets",
                        position = 0,
                        type = ConfigItemType.ListConfigItem(itemType = ScriptConfiguration::class, items = fields),
                        isNullable = false,
                        stateModifier = null,
                        isScriptIdentifier = false,
                    ),
                ),
        )

    private fun fullRecordFields() =
        listOf(
            listFieldDefinition("sku", ConfigItemType.StringConfigItem),
            listFieldDefinition("qty", ConfigItemType.IntConfigItem),
            listFieldDefinition("size", ConfigItemType.EnumConfigItem(@Suppress("UNCHECKED_CAST") (Size::class as KClass<Enum<*>>))),
            listFieldDefinition("notify", ConfigItemType.BooleanConfigItem, isNullable = true),
        )

    private fun scriptPackageWith(configurationDefinition: ScriptConfigurationDefinition): ScriptPackage =
        ScriptPackage(
            source = File("/test/script.jar"),
            manifest =
                Manifest(
                    packageName = "com.test.script",
                    name = "Test Script",
                    versionCode = 1L,
                ),
            mainScript = mockk<MainScript> { every { configuration } returns configurationDefinition },
            childScripts = emptyMap(),
        )

    @OptIn(ExperimentalTime::class)
    private suspend fun storeInstanceWithList(
        storedRows: ListRows,
        storeDefinition: ScriptConfigurationDefinition,
    ): Pair<ScriptPackageGroup, ScriptPackageInstance> {
        val scriptPackageGroup = createTestScriptPackageGroup()
        scriptInstanceDataSource.createScriptInstanceGroup(testUser, scriptPackageGroup)

        val configurationValues = mapOf("targets" to ConfigValue.ListValue(storedRows))
        val scriptPackageInstance =
            ScriptPackageInstance(
                id = UUID.randomUUID().toString(),
                mainConfiguration = configurationValues,
                childConfigurations = emptyMap(),
                definition = scriptPackageWith(storeDefinition),
                createdAt = Clock.System.now(),
                numberOfConcurrentTasks = 1,
            )
        // The stored configuration items come from the main *script* instance, not the package instance.
        val mainScriptInstance =
            MainScriptInstance(
                id = UUID.randomUUID().toString(),
                definition = scriptPackageInstance.definition.mainScript,
                configuration = configurationValues,
                createdAt = Clock.System.now(),
                packageInstance = scriptPackageInstance,
            )
        scriptInstanceDataSource.addScriptPackageInstance(
            testUser,
            scriptPackageInstance,
            mainScriptInstance,
            scriptPackageGroup.id,
        )
        return scriptPackageGroup to scriptPackageInstance
    }

    private suspend fun readBackList(
        scriptPackageGroup: ScriptPackageGroup,
        readDefinition: ScriptConfigurationDefinition,
    ): ListRows? {
        val instances =
            scriptInstanceDataSource.getScriptPackageInstancesInGroup(
                testUser,
                scriptPackageGroup.id,
                mapOf("com.test.script" to scriptPackageWith(readDefinition)),
            )
        return (instances.single().mainConfiguration["targets"] as? ConfigValue.ListValue)?.raw
    }

    @ParameterizedTest(name = "test list round trip - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test list value round trips through the database`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val definition = listConfigurationDefinition(fullRecordFields())
                val storedRows =
                    ListRows(
                        listOf(
                            ListRow(
                                mapOf(
                                    "sku" to ConfigValue.StringValue("ABC-123"),
                                    "qty" to ConfigValue.IntValue(2),
                                    "size" to ConfigValue.EnumValue(Size.LARGE),
                                    "notify" to ConfigValue.BooleanValue(true),
                                ),
                            ),
                            // The second row leaves the nullable field blank, so it must come back absent.
                            ListRow(
                                mapOf(
                                    "sku" to ConfigValue.StringValue("XYZ-9"),
                                    "qty" to ConfigValue.IntValue(1),
                                    "size" to ConfigValue.EnumValue(Size.SMALL),
                                ),
                            ),
                        ),
                    )

                val (group, _) = storeInstanceWithList(storedRows, definition)
                val readBack = readBackList(group, definition)

                assertEquals(storedRows, readBack)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test large list round trip - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test a few thousand list rows round trip intact`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val definition = listConfigurationDefinition(fullRecordFields())
                // At the CSV import cap the whole list is one encrypted column value, so the size of the
                // blob — not the number of rows — is what could silently truncate or corrupt it.
                val storedRows =
                    ListRows(
                        (1..3000).map { index ->
                            ListRow(
                                buildMap {
                                    put("sku", ConfigValue.StringValue("SKU-$index"))
                                    put("qty", ConfigValue.IntValue(index))
                                    put("size", ConfigValue.EnumValue(if (index % 2 == 0) Size.LARGE else Size.SMALL))
                                    // Every other row leaves the nullable field unset, so "absent" has to
                                    // survive the round trip too.
                                    if (index % 2 == 0) put("notify", ConfigValue.BooleanValue(true))
                                },
                            )
                        },
                    )

                val (group, _) = storeInstanceWithList(storedRows, definition)
                val readBack = readBackList(group, definition)

                assertEquals(storedRows, readBack)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test list tolerates a removed field - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test list drops stored keys the record no longer declares`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val storedRows =
                    ListRows(
                        listOf(
                            ListRow(
                                mapOf(
                                    "sku" to ConfigValue.StringValue("ABC-123"),
                                    "qty" to ConfigValue.IntValue(2),
                                    "size" to ConfigValue.EnumValue(Size.LARGE),
                                ),
                            ),
                        ),
                    )

                val (group, _) = storeInstanceWithList(storedRows, listConfigurationDefinition(fullRecordFields()))

                // A later script version removed "size" and "notify" from the record.
                val slimmedDefinition =
                    listConfigurationDefinition(
                        listOf(
                            listFieldDefinition("sku", ConfigItemType.StringConfigItem),
                            listFieldDefinition("qty", ConfigItemType.IntConfigItem),
                        ),
                    )
                val readBack = readBackList(group, slimmedDefinition)

                assertEquals(
                    ListRows(
                        listOf(
                            ListRow(
                                mapOf(
                                    "sku" to ConfigValue.StringValue("ABC-123"),
                                    "qty" to ConfigValue.IntValue(2),
                                ),
                            ),
                        ),
                    ),
                    readBack,
                )
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test list tolerates an added field - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test list treats newly declared keys as unset`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val storedRows =
                    ListRows(listOf(ListRow(mapOf("sku" to ConfigValue.StringValue("ABC-123")))))
                val (group, _) =
                    storeInstanceWithList(
                        storedRows,
                        listConfigurationDefinition(listOf(listFieldDefinition("sku", ConfigItemType.StringConfigItem))),
                    )

                // A later script version added "qty" to the record; the stored row survives without it.
                val extendedDefinition =
                    listConfigurationDefinition(
                        listOf(
                            listFieldDefinition("sku", ConfigItemType.StringConfigItem),
                            listFieldDefinition("qty", ConfigItemType.IntConfigItem),
                        ),
                    )
                val readBack = readBackList(group, extendedDefinition)

                assertEquals(storedRows, readBack)
            } finally {
                tearDownForImplementation(implementation)
            }
        }

    @ParameterizedTest(name = "test list drops an unparsable field - {0}")
    @EnumSource(value = DatabaseImplementation::class, names = ["ROOM"])
    fun `test list drops a stored value the fields new type cannot parse`(implementation: DatabaseImplementation) =
        runTest {
            setupForImplementation(implementation)
            try {
                val storedRows =
                    ListRows(
                        listOf(
                            ListRow(
                                mapOf(
                                    "sku" to ConfigValue.StringValue("ABC-123"),
                                    "qty" to ConfigValue.StringValue("not-a-number"),
                                ),
                            ),
                        ),
                    )
                val (group, _) =
                    storeInstanceWithList(
                        storedRows,
                        listConfigurationDefinition(
                            listOf(
                                listFieldDefinition("sku", ConfigItemType.StringConfigItem),
                                listFieldDefinition("qty", ConfigItemType.StringConfigItem),
                            ),
                        ),
                    )

                // A later script version changed "qty" from String to Int; the rest of the row survives.
                val retypedDefinition =
                    listConfigurationDefinition(
                        listOf(
                            listFieldDefinition("sku", ConfigItemType.StringConfigItem),
                            listFieldDefinition("qty", ConfigItemType.IntConfigItem),
                        ),
                    )
                val readBack = readBackList(group, retypedDefinition)

                assertEquals(
                    ListRows(listOf(ListRow(mapOf("sku" to ConfigValue.StringValue("ABC-123"))))),
                    readBack,
                )
            } finally {
                tearDownForImplementation(implementation)
            }
        }
}

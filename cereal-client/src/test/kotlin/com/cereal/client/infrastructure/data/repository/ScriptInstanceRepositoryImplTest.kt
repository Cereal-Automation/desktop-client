package com.cereal.client.infrastructure.data.repository

import com.cereal.client.application.exception.ScriptInstanceNotFoundException
import com.cereal.client.domain.model.script.ChildScript
import com.cereal.client.domain.model.script.ChildScriptInstance
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.user.User
import com.cereal.client.fixtures.FakeSubscriptionDataSource
import com.cereal.client.fixtures.InMemoryScriptInstanceDataSource
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.database.ScriptInstanceDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemScriptsDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.ManifestDefinition
import com.cereal.client.infrastructure.data.datasource.filesystem.ScriptPackageDefinition
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ScriptInstanceRepositoryImplTest {
    private val scriptInstanceDataSource = InMemoryScriptInstanceDataSource()
    private val fileSystemScriptsDataSource = mockk<FileSystemScriptsDataSource>(relaxed = true)
    private val userSession = mockk<UserSession>(relaxed = true)
    private val subscriptionDataSource = FakeSubscriptionDataSource()
    private lateinit var repository: ScriptInstanceRepositoryImpl

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
        startKoin { }
        coEvery { userSession.requireUser() } returns user
        repository =
            ScriptInstanceRepositoryImpl(
                scriptInstanceDataSource = scriptInstanceDataSource,
                fileSystemScriptsDataSource = fileSystemScriptsDataSource,
                userSession = userSession,
                subscriptionDataSource = subscriptionDataSource,
            )
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    /**
     * Make the filesystem data source advertise definitions for the given package names so the
     * in-memory script-instance data source includes instances of those packages in its getters
     * (it filters on the package-name -> definition map the repository builds).
     */
    private fun seedDefinitions(vararg packageNames: String) {
        // MockK: filesystem edge
        every { fileSystemScriptsDataSource.getScriptDefinitions(user) } returns
            flowOf(packageNames.map { aScriptPackageDefinition(it) })
        packageNames.forEach { packageName ->
            // MockK: filesystem edge
            coEvery { fileSystemScriptsDataSource.getScriptPackageDefinition(packageName, user) } returns
                aScriptPackageDefinition(packageName)
        }
    }

    @Test
    fun `addScriptPackageInstance makes the instance visible via getScriptPackageInstances`() =
        runTest {
            seedDefinitions("com.example.script")
            val instance = aScriptPackageInstance("instance-1", "com.example.script")

            repository.addScriptPackageInstance("group-1", instance, mainInstanceFor(instance))

            assertEquals(listOf(instance), repository.getScriptPackageInstances())
        }

    @Test
    fun `addScriptPackageInstance makes the instance visible in its group`() =
        runTest {
            seedDefinitions("com.example.script")
            val instance = aScriptPackageInstance("instance-1", "com.example.script")
            repository.addScriptPackageInstance("group-1", instance, mainInstanceFor(instance))

            val inGroup = repository.getScriptPackageInstancesInGroup(ScriptPackageGroup("group-1", "Group"))
            val inOtherGroup = repository.getScriptPackageInstancesInGroup(ScriptPackageGroup("group-2", "Other"))

            assertEquals(listOf(instance), inGroup)
            assertTrue(inOtherGroup.isEmpty())
        }

    @Test
    fun `getScriptPackageInstances with packageName filters by package name`() =
        runTest {
            seedDefinitions("com.example.script", "com.other.script")
            val matching = aScriptPackageInstance("instance-1", "com.example.script")
            val other = aScriptPackageInstance("instance-2", "com.other.script")
            repository.addScriptPackageInstance("group-1", matching, mainInstanceFor(matching))
            repository.addScriptPackageInstance("group-1", other, mainInstanceFor(other))

            val result = repository.getScriptPackageInstances("com.example.script")

            assertEquals(listOf(matching), result)
        }

    @Test
    fun `getScriptPackageInstancesInGroupFlow reflects added instances`() =
        runTest {
            seedDefinitions("com.example.script")
            val instance = aScriptPackageInstance("instance-1", "com.example.script")
            repository.addScriptPackageInstance("group-1", instance, mainInstanceFor(instance))

            val result = repository.getScriptPackageInstancesInGroupFlow("group-1").first()

            assertEquals(listOf(instance), result)
        }

    @Test
    fun `addChildScriptInstance makes the child visible via getScriptInstances`() =
        runTest {
            seedDefinitions("com.example.script")
            val packageInstance = aScriptPackageInstance("instance-1", "com.example.script")
            val main = mainInstanceFor(packageInstance)
            repository.addScriptPackageInstance("group-1", packageInstance, main)
            val child = childInstanceFor(packageInstance, main, "child-1")

            repository.addChildScriptInstance(main, "child-name", child)

            val instances = repository.getScriptInstances(packageInstance)
            assertTrue(child in instances)
            assertTrue(main in instances)
        }

    @Test
    fun `getScriptPackageInstance returns the stored instance`() =
        runTest {
            seedDefinitions("com.example.script")
            val instance = aScriptPackageInstance("instance-1", "com.example.script")
            repository.addScriptPackageInstance("group-1", instance, mainInstanceFor(instance))

            val result = repository.getScriptPackageInstance("instance-1")

            assertEquals(instance, result)
        }

    @Test
    fun `getScriptPackageInstance throws when the package name is not found`() =
        runTest {
            seedDefinitions("com.example.script")

            assertFailsWith<ScriptInstanceNotFoundException> {
                repository.getScriptPackageInstance("missing")
            }
        }

    @Test
    fun `getScriptPackageInstance throws when the definition is not found`() =
        runTest {
            seedDefinitions("com.example.script")
            val instance = aScriptPackageInstance("instance-1", "com.example.script")
            repository.addScriptPackageInstance("group-1", instance, mainInstanceFor(instance))
            // Definition lookup now returns null for this package, even though the instance exists.
            // MockK: filesystem edge
            coEvery { fileSystemScriptsDataSource.getScriptPackageDefinition("com.example.script", user) } returns null

            assertFailsWith<ScriptInstanceNotFoundException> {
                repository.getScriptPackageInstance("instance-1")
            }
        }

    @Test
    fun `getScriptPackageInstance throws when the instance is not found`() =
        runTest {
            // A package name and definition resolve, but the instance lookup yields null.
            // MockK: inject external failure to verify error translation to ScriptInstanceNotFoundException
            val failingDataSource = mockk<ScriptInstanceDataSource>(relaxed = true)
            coEvery { failingDataSource.getScriptPackageNameById(user, "instance-1") } returns "com.example.script"
            coEvery { failingDataSource.getScriptPackageInstance(eq(user), eq("instance-1"), any()) } returns null
            seedDefinitions("com.example.script")
            val repo =
                ScriptInstanceRepositoryImpl(
                    scriptInstanceDataSource = failingDataSource,
                    fileSystemScriptsDataSource = fileSystemScriptsDataSource,
                    userSession = userSession,
                    subscriptionDataSource = subscriptionDataSource,
                )

            assertFailsWith<ScriptInstanceNotFoundException> {
                repo.getScriptPackageInstance("instance-1")
            }
        }

    @Test
    fun `deleteScriptPackageInstance removes the instance`() =
        runTest {
            seedDefinitions("com.example.script")
            val instance = aScriptPackageInstance("instance-1", "com.example.script")
            val main = mainInstanceFor(instance)
            repository.addScriptPackageInstance("group-1", instance, main)

            repository.deleteScriptPackageInstance(instance)

            assertTrue(repository.getScriptPackageInstances().isEmpty())
        }

    @Test
    fun `updateScriptPackageInstanceGroup moves the instance to the new group`() =
        runTest {
            seedDefinitions("com.example.script")
            val instance = aScriptPackageInstance("instance-1", "com.example.script")
            repository.addScriptPackageInstance("group-1", instance, mainInstanceFor(instance))

            repository.updateScriptPackageInstanceGroup(instance, "group-2")

            assertTrue(repository.getScriptPackageInstancesInGroup(ScriptPackageGroup("group-1", "Group")).isEmpty())
            assertEquals(
                listOf(instance),
                repository.getScriptPackageInstancesInGroup(ScriptPackageGroup("group-2", "Other")),
            )
        }

    private fun aScriptPackageInstance(
        id: String,
        packageName: String,
    ) = ScriptPackageInstance(
        id = id,
        mainConfiguration = emptyMap(),
        childConfigurations = emptyMap(),
        definition = aScriptPackage(packageName),
        createdAt = Instant.fromEpochMilliseconds(0),
        numberOfConcurrentTasks = 1,
    )

    private fun mainInstanceFor(packageInstance: ScriptPackageInstance) =
        MainScriptInstance(
            id = "main-${packageInstance.id}",
            definition = mockk<MainScript>(relaxed = true),
            configuration = emptyMap(),
            createdAt = Instant.fromEpochMilliseconds(0),
            packageInstance = packageInstance,
        )

    private fun childInstanceFor(
        packageInstance: ScriptPackageInstance,
        parent: MainScriptInstance,
        id: String,
    ) = ChildScriptInstance(
        id = id,
        definition = mockk<ChildScript>(relaxed = true),
        configuration = emptyMap(),
        createdAt = Instant.fromEpochMilliseconds(0),
        packageInstance = packageInstance,
        parent = parent,
        params = null,
    )

    private fun aScriptPackage(packageName: String) =
        ScriptPackage(
            source = File("/tmp/fake.jar"),
            manifest =
                Manifest(
                    packageName = packageName,
                    name = "Test Script",
                    versionCode = 1L,
                ),
            mainScript = mockk(relaxed = true),
            childScripts = emptyMap(),
        )

    private fun aScriptPackageDefinition(packageName: String) =
        ScriptPackageDefinition(
            source = File("/tmp/fake.jar"),
            manifest =
                ManifestDefinition(
                    packageName = packageName,
                    name = "Test Script",
                    versionCode = 1L,
                ),
            mainScript = mockk(relaxed = true),
            childScripts = emptyMap(),
        )
}

package com.cereal.client.application.interactor.script

import com.cereal.client.application.exception.ClientUpdateRequiredException
import com.cereal.client.application.script.ScriptInstanceFactory
import com.cereal.client.application.script.ScriptLicenseChecker
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.ScopeLinker
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAuthProvider
import com.cereal.sdk.ScriptConfiguration
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Behavioural tests for [StartScriptInteractor] wired to a real [ScriptLicenseChecker] over an
 * in-memory [InMemoryAuthProvider] (seeded subscriptions) and in-memory repositories. Licensing and
 * capacity are asserted against observable outcomes (instance persisted, or the start refused) rather
 * than mocked calls.
 */
@OptIn(ExperimentalTime::class)
class StartScriptInteractorTest {
    private lateinit var scriptInstanceRepository: InMemoryScriptInstanceRepository
    private lateinit var applicationRepository: InMemoryApplicationRepository
    private lateinit var taskManager: TaskManager
    private val preferences = InMemoryApplicationPreferenceRepository()

    /** No-op scope linker so the real [ScriptInstanceFactory] can create instances without DI wiring. */
    private val noOpScopeLinker =
        object : ScopeLinker {
            override fun linkScriptInstanceToPackage(scriptInstance: ScriptInstance) = Unit

            override fun linkTaskToScriptInstance(task: JobTask) = Unit
        }

    @BeforeEach
    fun setUp() {
        scriptInstanceRepository = InMemoryScriptInstanceRepository()
        applicationRepository = InMemoryApplicationRepository()
        taskManager = mockk(relaxed = true)
    }

    /** Builds the interactor with a real license checker over the given seeded [subscriptions]. */
    private fun interactorWith(subscriptions: List<Subscription> = emptyList()): StartScriptInteractor =
        StartScriptInteractor(
            scriptInstanceRepository = scriptInstanceRepository,
            taskManager = taskManager,
            scriptLicenseChecker = ScriptLicenseChecker(InMemoryAuthProvider(subscriptions = subscriptions), preferences),
            applicationRepository = applicationRepository,
            scriptInstanceFactory = ScriptInstanceFactory(noOpScopeLinker),
        )

    @Test
    fun `run throws ClientUpdateRequiredException when the script requires a newer SDK`() =
        runTest {
            // InMemoryApplicationRepository reports SDK 1.0.0; the script requires 2.0. Thrown before licensing.
            assertFailsWith<ClientUpdateRequiredException> {
                interactorWith().run(params(sdkVersion = "2.0.0"))
            }
        }

    @Test
    fun `run throws UnlicensedException when the script is not licensed`() =
        runTest {
            assertFailsWith<UnlicensedException> {
                interactorWith(subscriptions = emptyList()).run(params())
            }
        }

    @Test
    fun `run persists the package instance and creates tasks on the happy path`() =
        runTest {
            val interactor = interactorWith(subscriptions = listOf(subscription(PACKAGE, ScriptCapacity.None)))

            val result = interactor.run(params())

            // Observable state: the instance is stored in the repository...
            assertEquals(listOf(result), scriptInstanceRepository.getScriptPackageInstances())
            // ...and the task manager was asked to create tasks for it.
            coVerify(exactly = 1) { taskManager.createTasks(any()) }
        }

    @Test
    fun `run starts when the run's record count is under the entitled capacity`() =
        runTest {
            val interactor = interactorWith(subscriptions = listOf(subscription(PACKAGE, ScriptCapacity.Limited(100, "records"))))

            val result = interactor.run(paramsWithDataset(records = 50))

            assertEquals(listOf(result), scriptInstanceRepository.getScriptPackageInstances())
        }

    @Test
    fun `run starts when the run's record count is exactly at the entitled capacity`() =
        runTest {
            val interactor = interactorWith(subscriptions = listOf(subscription(PACKAGE, ScriptCapacity.Limited(100, "records"))))

            val result = interactor.run(paramsWithDataset(records = 100))

            assertEquals(listOf(result), scriptInstanceRepository.getScriptPackageInstances())
        }

    @Test
    fun `run blocks with CapacityExceededException when the record count exceeds the entitled capacity`() =
        runTest {
            val interactor = interactorWith(subscriptions = listOf(subscription(PACKAGE, ScriptCapacity.Limited(100, "records"))))

            val exception =
                assertFailsWith<CapacityExceededException> {
                    interactor.run(paramsWithDataset(records = 150))
                }

            assertEquals(150, exception.recordCount)
            assertEquals(100, exception.capacity)
            // Blocked: nothing was persisted and no tasks were created.
            assertTrue(scriptInstanceRepository.getScriptPackageInstances().isEmpty())
            coVerify(exactly = 0) { taskManager.createTasks(any()) }
        }

    @Test
    fun `run never blocks when capacity is unlimited`() =
        runTest {
            val interactor = interactorWith(subscriptions = listOf(subscription(PACKAGE, ScriptCapacity.Unlimited("records"))))

            val result = interactor.run(paramsWithDataset(records = 10_000))

            assertEquals(listOf(result), scriptInstanceRepository.getScriptPackageInstances())
        }

    @Test
    fun `run never blocks when the script has no capacity concept`() =
        runTest {
            val interactor = interactorWith(subscriptions = listOf(subscription(PACKAGE, ScriptCapacity.None)))

            val result = interactor.run(paramsWithDataset(records = 10_000))

            assertEquals(listOf(result), scriptInstanceRepository.getScriptPackageInstances())
        }

    private fun params(
        sdkVersion: String? = null,
        ignoreConcurrencyConflicts: Boolean = true,
    ): StartScriptInteractor.Params {
        val basePackage = fixtures.aScriptPackage(PACKAGE)
        val scriptPackage = basePackage.copy(manifest = basePackage.manifest.copy(sdkVersion = sdkVersion))
        return StartScriptInteractor.Params(
            groupId = "group-1",
            scriptPackage = scriptPackage,
            mainScriptConfiguration = emptyMap(),
            childConfigurations = emptyMap(),
            numberOfConcurrentTasks = 1,
            ignoreConcurrencyConflicts = ignoreConcurrencyConflicts,
        )
    }

    /** Params for a script whose main configuration has a grouped dataset of [records] items. */
    private fun paramsWithDataset(records: Int): StartScriptInteractor.Params {
        val base = fixtures.aScriptPackage(PACKAGE)
        val scriptPackage =
            base.copy(
                mainScript =
                    base.mainScript.copy(
                        configuration =
                            ScriptConfigurationDefinition(
                                scriptConfigurationClass = ScriptConfiguration::class,
                                configurationItems = listOf(groupedItemDef(DATASET_KEY)),
                            ),
                    ),
            )
        return StartScriptInteractor.Params(
            groupId = "group-1",
            scriptPackage = scriptPackage,
            mainScriptConfiguration = mapOf(DATASET_KEY to datasetValue(records)),
            childConfigurations = emptyMap(),
            numberOfConcurrentTasks = 1,
            ignoreConcurrencyConflicts = true,
        )
    }

    private companion object {
        private const val PACKAGE = "com.cereal.test"
        private const val DATASET_KEY = "dataset"

        private fun groupedItemDef(key: String) =
            ScriptConfigurationItemDefinition(
                name = key,
                description = "desc-$key",
                key = key,
                position = 0,
                type = ConfigItemType.GroupedConfigItem(items = emptyList()),
                isNullable = false,
                stateModifier = null,
                isScriptIdentifier = false,
                defaultValue = null,
            )

        private fun datasetValue(numberOfItems: Int) =
            ConfigValue.CustomDatasetGroupValue(
                CustomDatasetGroup(
                    id = "ds",
                    name = "Dataset",
                    numberOfItems = numberOfItems,
                    itemDefinitions = emptyList(),
                    items = emptySequence(),
                    createdAt = Instant.fromEpochMilliseconds(0),
                ),
            )

        private fun listing(
            publicId: String,
            capacity: ScriptCapacity,
        ) = ScriptEntitlement(
            publicIdentifier = publicId,
            title = publicId,
            latestRelease = null,
            latestDraftRelease = null,
            shortDescription = null,
            price = null,
            capacity = capacity,
        )

        private fun subscription(
            publicId: String,
            capacity: ScriptCapacity,
        ) = Subscription(id = "sub-$publicId", entitlement = listing(publicId, capacity))
    }
}

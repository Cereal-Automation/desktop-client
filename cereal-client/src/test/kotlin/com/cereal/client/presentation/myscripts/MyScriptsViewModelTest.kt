package com.cereal.client.presentation.myscripts

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.agent.AI_BROWSER_AGENT_PACKAGE
import com.cereal.client.domain.model.marketplace.MarketplaceDirection
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.marketplace.MarketplaceSort
import com.cereal.client.domain.model.marketplace.PaginatedResult
import com.cereal.client.domain.model.marketplace.ScriptOwner
import com.cereal.client.domain.model.marketplace.ScriptSubscriptionResult
import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.provider.MarketplaceProvider
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import com.cereal.client.infrastructure.di.Injector
import com.cereal.client.infrastructure.di.modules.InMemoryProviderModule
import com.cereal.client.infrastructure.di.modules.InMemoryRepositoryModule
import com.cereal.client.infrastructure.provider.inmemory.InMemoryMarketplaceProvider
import com.cereal.client.presentation.error.ErrorAction
import com.cereal.client.presentation.myscripts.MyScriptsViewModel.InstalledScriptStatus
import com.cereal.client.presentation.tasks.script.overview.ScriptRequest
import fixtures.aScriptPackage
import fixtures.aScriptPackageWithVariant
import fixtures.aTask
import fixtures.anAgentPackage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent

/**
 * Drives [MyScriptsViewModel] through the real interactor graph on in-memory repositories; only the
 * marketplace provider is swapped per test to seed listings or force a failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MyScriptsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val startedInstances = mutableListOf<ScriptRequest>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun `lists installed scripts sorted by name`() =
        runTest(dispatcher) {
            val vm = createViewModel(installed = listOf(installed("com.b", "Beta"), installed("com.a", "Alpha")))

            assertEquals(listOf("Alpha", "Beta"), vm.rows.value.map { it.scriptPackage.manifest.name })
        }

    @Test
    fun `sorts by the displayed name, preferring the listing title`() =
        runTest(dispatcher) {
            val vm =
                createViewModel(
                    installed = listOf(installed("com.z", "Zed"), installed("com.b", "Beta")),
                    marketplace = InMemoryMarketplaceProvider(listOf(listing("com.z", title = "Alpha Listed"))),
                )

            assertEquals(listOf("com.z", "com.b"), vm.rows.value.map { it.scriptPackage.manifest.packageName })
        }

    @Test
    fun `matches installed scripts to listings by public identifier, beyond the first page`() =
        runTest(dispatcher) {
            val fillers = (1..60).map { listing("com.filler.$it") }
            val vm =
                createViewModel(
                    installed = listOf(installed("com.a", "Alpha")),
                    marketplace = InMemoryMarketplaceProvider(fillers + listing("com.a", title = "Alpha Listed")),
                )

            val row = vm.rows.value.single()
            assertEquals("Alpha Listed", row.listing?.title)
            assertTrue(row.isClickable)
            assertFalse(row.isListingLoading)
        }

    @Test
    fun `unmatched scripts stay listed, are not clickable and can be removed`() =
        runTest(dispatcher) {
            val unmatched = installed("com.team", "Team Script")
            val vm =
                createViewModel(
                    installed = listOf(unmatched, installed("com.a", "Alpha")),
                    marketplace = InMemoryMarketplaceProvider(listOf(listing("com.a"))),
                )

            val row = vm.rows.value.single { it.scriptPackage == unmatched }
            assertNull(row.listing)
            assertFalse(row.isClickable)

            vm.onRemoveClicked(unmatched)
            vm.onRemoveConfirmed()
            advanceUntilIdle()

            assertEquals(listOf("Alpha"), vm.rows.value.map { it.scriptPackage.manifest.name })
        }

    @Test
    fun `a failed marketplace fetch leaves rows manifest-only without surfacing an error`() =
        runTest(dispatcher) {
            val vm = createViewModel(installed = listOf(installed("com.a", "Alpha")), marketplace = FailingMarketplaceProvider)

            val row = vm.rows.value.single()
            assertNull(row.listing)
            assertFalse(row.isClickable)
            assertFalse(row.isListingLoading)
            assertEquals(ErrorAction.None, vm.errorAction.value)
        }

    @Test
    fun `excludes the AI browser agent`() =
        runTest(dispatcher) {
            val vm = createViewModel(installed = listOf(anAgentPackage(), installed("com.a", "Alpha")))

            assertEquals(listOf("com.a"), vm.rows.value.map { it.scriptPackage.manifest.packageName })
        }

    @Test
    fun `shows the most urgent status per row`() =
        runTest(dispatcher) {
            val vm =
                createViewModel(
                    installed =
                        listOf(
                            installed("com.a", "A", versionCode = 1, sdkVersion = "9.0.0"),
                            installed("com.b", "B", versionCode = 1),
                            installed("com.c", "C"),
                            installed("com.d", "D"),
                        ),
                    marketplace = InMemoryMarketplaceProvider(listOf(listing("com.a", versionCode = 2), listing("com.b", versionCode = 2))),
                    runningPackages = listOf("com.a", "com.b", "com.c"),
                )

            assertEquals(
                mapOf(
                    "com.a" to InstalledScriptStatus.CLIENT_UPDATE_REQUIRED,
                    "com.b" to InstalledScriptStatus.UPDATE_AVAILABLE,
                    "com.c" to InstalledScriptStatus.RUNNING_TASKS,
                    "com.d" to null,
                ),
                vm.rows.value.associate { it.scriptPackage.manifest.packageName to it.status },
            )
        }

    @Test
    fun `update available only when the listing's latest release is newer than the installed one`() =
        runTest(dispatcher) {
            val vm =
                createViewModel(
                    installed =
                        listOf(
                            installed("com.older", "Older", versionCode = 3),
                            installed("com.same", "Same", versionCode = 3),
                            installed("com.newer", "Newer", versionCode = 3),
                        ),
                    marketplace =
                        InMemoryMarketplaceProvider(
                            listOf(
                                listing("com.older", versionCode = 4),
                                listing("com.same", versionCode = 3),
                                listing("com.newer", versionCode = 2),
                            ),
                        ),
                )

            assertEquals(
                mapOf("com.newer" to null, "com.older" to InstalledScriptStatus.UPDATE_AVAILABLE, "com.same" to null),
                vm.rows.value.associate { it.scriptPackage.manifest.packageName to it.status },
            )
        }

    @Test
    fun `removal is refused while the script has running tasks`() =
        runTest(dispatcher) {
            val script = installed("com.a", "Alpha")
            val vm = createViewModel(installed = listOf(script), runningPackages = listOf("com.a"))

            vm.onRemoveClicked(script)
            vm.onRemoveConfirmed()
            advanceUntilIdle()

            assertEquals(listOf("com.a"), vm.rows.value.map { it.scriptPackage.manifest.packageName })
            assertTrue(vm.errorAction.value is ErrorAction.Message)
        }

    @Test
    fun `opening a listed row and starting a new instance closes the dialog and forwards the identifier`() =
        runTest(dispatcher) {
            val vm =
                createViewModel(
                    installed = listOf(installed("com.a", "Alpha")),
                    marketplace = InMemoryMarketplaceProvider(listOf(listing("com.a"))),
                )

            vm.onRowClicked(vm.rows.value.single())
            assertEquals("com.a", vm.selectedScript.value?.publicIdentifier)

            vm.onStartNewInstance("com.a")

            assertNull(vm.selectedScript.value)
            assertEquals(listOf(ScriptRequest("com.a")), startedInstances)
        }

    @Test
    fun `starting a new instance from a variant row requests that variant`() =
        runTest(dispatcher) {
            val agent = aScriptPackageWithVariant(AI_BROWSER_AGENT_PACKAGE, variantId = "checkout", variantName = "Checkout")
            val vm =
                createViewModel(
                    installed = listOf(agent),
                    marketplace = InMemoryMarketplaceProvider(listOf(listing(AI_BROWSER_AGENT_PACKAGE))),
                )

            vm.onRowClicked(vm.rows.value.single { it.variantId == "checkout" })
            vm.onStartNewInstance(AI_BROWSER_AGENT_PACKAGE)

            assertEquals(listOf(ScriptRequest(AI_BROWSER_AGENT_PACKAGE, "checkout")), startedInstances)
        }

    private suspend fun TestScope.createViewModel(
        installed: List<ScriptPackage>,
        marketplace: MarketplaceProvider = InMemoryMarketplaceProvider(),
        runningPackages: List<String> = emptyList(),
    ): MyScriptsViewModel {
        stopKoin()
        startKoin {
            modules(
                Injector.appModules(InMemoryRepositoryModule.modules, InMemoryProviderModule.modules) +
                    module {
                        factory { CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher) }
                        single<MarketplaceProvider> { marketplace }
                    },
            )
        }
        (KoinJavaComponent.get<ScriptRepository>(ScriptRepository::class.java) as InMemoryScriptRepository).seed(installed)
        val tasksRepository = KoinJavaComponent.get<TasksRepository>(TasksRepository::class.java)
        val runningTasks = runningPackages.map { aTask("task-$it", it, running = true) }
        runningTasks.forEach { tasksRepository.addTask(it) }
        // The removal guard finds running tasks through the package's instances, so register those too.
        (KoinJavaComponent.get<ScriptInstanceRepository>(ScriptInstanceRepository::class.java) as InMemoryScriptInstanceRepository)
            .seed(runningTasks.map { it.scriptInstance.packageInstance })

        val vm =
            KoinJavaComponent.get<MyScriptsViewModel>(
                MyScriptsViewModel::class.java,
                parameters = { parametersOf(CoroutineScope(dispatcher), { request: ScriptRequest -> startedInstances += request }) },
            )
        advanceUntilIdle()
        return vm
    }

    private fun installed(
        packageName: String,
        name: String,
        versionCode: Long = 1,
        sdkVersion: String? = null,
    ): ScriptPackage {
        val base = aScriptPackage(packageName)
        return base.copy(manifest = base.manifest.copy(name = name, versionCode = versionCode, sdkVersion = sdkVersion))
    }

    private fun listing(
        publicIdentifier: String,
        title: String = publicIdentifier,
        versionCode: Long = 1,
    ) = MarketplaceScript(
        id = publicIdentifier,
        publicIdentifier = publicIdentifier,
        title = title,
        shortDescription = "About $title",
        developer = ScriptOwner(name = "dev"),
        latestRelease = Release(versionName = "1.0.$versionCode", versionCode = versionCode, releaseNotes = null),
    )

    private object FailingMarketplaceProvider : MarketplaceProvider {
        override suspend fun getMarketplaceScripts(
            search: String?,
            sort: MarketplaceSort,
            direction: MarketplaceDirection,
            community: Boolean?,
            isFree: Boolean?,
            page: Int,
            perPage: Int,
        ): PaginatedResult<MarketplaceScript> = throw java.io.IOException("Marketplace unreachable")

        override suspend fun subscribeToScript(publicScriptId: String): ScriptSubscriptionResult = ScriptSubscriptionResult.Subscribed

        override suspend fun unsubscribeFromScript(publicScriptId: String) = Unit
    }
}

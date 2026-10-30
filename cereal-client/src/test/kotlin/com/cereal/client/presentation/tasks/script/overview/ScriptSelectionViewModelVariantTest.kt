package com.cereal.client.presentation.tasks.script.overview

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.notification.HasNotificationChannelsConfiguredInteractor
import com.cereal.client.application.interactor.script.GetScriptCapacityInteractor
import com.cereal.client.application.interactor.script.GetScriptConfigDefinitionInteractor
import com.cereal.client.application.interactor.script.GetScriptPackageInstancesByPackageNameInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.script.GetSdkVersionInteractor
import com.cereal.client.application.interactor.script.StartScriptInteractor
import com.cereal.client.application.interactor.settings.ai.HasLlmProviderConfigInteractor
import com.cereal.client.application.script.ScriptInstanceFactory
import com.cereal.client.application.script.ScriptLicenseChecker
import com.cereal.client.application.script.TaskSetupLimits
import com.cereal.client.domain.model.ScopeLinker
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryCustomDatasetRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLlmProviderConfigRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationSettingsRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAuthProvider
import com.cereal.client.presentation.error.ErrorAction
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.tasks.script.overview.configuration.ScriptConfigurationViewModel
import com.cereal.client.presentation.view.fields.state.StringTextFieldState
import com.cereal.client.presentation.view.group.GroupedListViewModel
import fixtures.VARIANT_FIELD
import fixtures.aScriptPackage
import fixtures.aScriptPackageInstance
import fixtures.aScriptPackageWithVariant
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Script selection with variants, wired to real interactors over in-memory repositories: the rows a user sees,
 * the configuration form a row opens and the instance that starting it creates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScriptSelectionViewModelVariantTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    private val scope = CoroutineScope(dispatcher)
    private val scriptRepository = InMemoryScriptRepository()
    private val scriptInstanceRepository = InMemoryScriptInstanceRepository()
    private val preferences = InMemoryApplicationPreferenceRepository()
    private val authProvider = InMemoryAuthProvider(subscriptions = listOf(subscription(AGENT), subscription(PLAIN)))

    private val agent = aScriptPackageWithVariant(AGENT, variantId = "checkout", variantName = "Checkout").named("Agent")
    private val plain = aScriptPackage(PLAIN).named("Plain")

    private val noOpScopeLinker =
        object : ScopeLinker {
            override fun linkScriptInstanceToPackage(scriptInstance: ScriptInstance) = Unit

            override fun linkTaskToScriptInstance(task: JobTask) = Unit
        }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        scriptRepository.seed(listOf(agent, plain))
        startKoin {
            modules(
                module {
                    factory { params ->
                        ScriptConfigurationViewModel(
                            scriptPackage = params.get<ScriptPackage>(1),
                            initialScriptPackageInstance = params.get<ScriptPackageInstance?>(2),
                            variantId = params.get<String?>(3),
                            scope = params.get<CoroutineScope>(0),
                            errorResolver = ErrorResolver(),
                            dispatcherProvider = dispatcherProvider,
                            getScriptConfigDefinitionInteractor =
                                GetScriptConfigDefinitionInteractor(InMemoryProxyRepository(), InMemoryCustomDatasetRepository()),
                            getScriptPackageInstancesByPackageNameInteractor =
                                GetScriptPackageInstancesByPackageNameInteractor(scriptInstanceRepository),
                            readCustomDatasetFileInteractor = mockk(relaxed = true),
                            readProxyFileInteractor = mockk(relaxed = true),
                            readListFileInteractor = mockk(relaxed = true),
                        )
                    }
                },
            )
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    private fun createViewModel(
        duplicate: ScriptPackageInstance? = null,
        request: ScriptRequest? = null,
    ) = ScriptSelectionViewModel(
        scope = scope,
        scriptPackageGroup = ScriptPackageGroup("g1", "Group 1"),
        initialScriptPackageInstance = duplicate,
        initialRequest = request,
        dispatcherProvider = dispatcherProvider,
        getScriptsInteractor = GetScriptsInteractor(scriptRepository),
        getScriptCapacityInteractor = GetScriptCapacityInteractor(ScriptLicenseChecker(authProvider, preferences)),
        getSdkVersionInteractor = GetSdkVersionInteractor(InMemoryApplicationRepository()),
        syncScriptsOnScriptSelectionInteractor = mockk(relaxed = true),
        startScriptInteractor =
            StartScriptInteractor(
                scriptInstanceRepository = scriptInstanceRepository,
                taskManager = mockk(relaxed = true),
                taskSetupLimits = TaskSetupLimits(ScriptLicenseChecker(authProvider, preferences)),
                applicationRepository = InMemoryApplicationRepository(),
                scriptInstanceFactory = ScriptInstanceFactory(noOpScopeLinker),
            ),
        updateScriptPackageInstanceInteractor = mockk(relaxed = true),
        hasNotificationChannelsConfiguredInteractor = HasNotificationChannelsConfiguredInteractor(InMemoryNotificationSettingsRepository()),
        hasLlmProviderConfigInteractor = HasLlmProviderConfigInteractor(InMemoryLlmProviderConfigRepository()),
        errorResolver = ErrorResolver(),
    ).also { dispatcher.scheduler.advanceUntilIdle() }

    @Suppress("UNCHECKED_CAST")
    private fun ScriptSelectionViewModel.rows() =
        ((overviewViewState.value as ScriptSelectionState.Overview.Filled).viewModel as GroupedListViewModel<Any?>).let { list ->
            list to list.state.value
        }

    private fun ScriptSelectionViewModel.selectedEntryName() = selectedEntry.value?.name

    private fun ScriptSelectionViewModel.selectedPackageTitle() =
        rows()
            .second
            .single { it.selected }
            .content.title

    private fun ScriptSelectionViewModel.selectRow(title: String) {
        val (list, rows) = rows()
        list.onItemClick(rows.first { it.content.title == title })
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun ScriptSelectionViewModel.pickEntry(name: String) {
        selectEntry(entryOptions.value.first { it.name == name })
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun ScriptSelectionViewModel.variantField() =
        (configurationViewState.value as ScriptSelectionState.Config.Selected)
            .scriptConfigurationViewModel.scriptConfigurationForm.value!!
            .mainConfigurationFormSection.configurationItemToFieldState.values
            .single() as StringTextFieldState

    /** Starts the selected row the way a user does, past the no-notification-channels warning. */
    private fun ScriptSelectionViewModel.start() {
        launchScript()
        dispatcher.scheduler.advanceUntilIdle()
        onNoNotificationChannelsWarningAccepted()
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun ScriptSelectionViewModel.formFieldKeys() =
        (configurationViewState.value as ScriptSelectionState.Config.Selected)
            .scriptConfigurationViewModel.scriptConfigurationForm.value!!
            .mainConfigurationFormSection.configurationItemToFieldState.keys
            .map { it.definition.key }

    @Test
    fun `lists one rail row per package under the package name`() {
        val viewModel = createViewModel()

        assertEquals(listOf("Agent", "Plain"), viewModel.rows().second.map { it.content.title })
    }

    @Test
    fun `a package with variants offers its default entry first, then each variant`() {
        val viewModel = createViewModel()

        assertEquals(listOf("Agent", "Checkout"), viewModel.entryOptions.value.map { it.name })
    }

    @Test
    fun `a package without variants offers only its default entry`() {
        val viewModel = createViewModel()

        viewModel.selectRow("Plain")

        assertEquals(listOf("Plain"), viewModel.entryOptions.value.map { it.name })
    }

    @Test
    fun `picking a variant opens the variant's configuration form`() {
        val viewModel = createViewModel()

        viewModel.pickEntry("Checkout")

        assertEquals(listOf(VARIANT_FIELD), viewModel.formFieldKeys())
    }

    @Test
    fun `switching back to a variant keeps what was typed in its form`() {
        val viewModel = createViewModel()
        viewModel.pickEntry("Checkout")
        viewModel.variantField().onValueChange("typed")

        viewModel.pickEntry("Agent")
        viewModel.pickEntry("Checkout")

        assertEquals("typed", viewModel.variantField().text)
    }

    @Test
    fun `switching to another package and back keeps what was typed in its forms`() {
        val viewModel = createViewModel()
        viewModel.pickEntry("Checkout")
        viewModel.variantField().onValueChange("typed")

        viewModel.selectRow("Plain")
        viewModel.selectRow("Agent")
        viewModel.pickEntry("Checkout")

        assertEquals("typed", viewModel.variantField().text)
    }

    @Test
    fun `while duplicating, edits survive switching to another package and back`() {
        val original = aScriptPackageInstance("original", AGENT, definition = agent, variantId = "checkout")
        val viewModel = createViewModel(duplicate = original)
        viewModel.variantField().onValueChange("edited")

        viewModel.selectRow("Plain")
        viewModel.selectRow("Agent")

        assertEquals("edited", viewModel.variantField().text)
    }

    @Test
    fun `an update to the selected package reopens it with the updated entries`() {
        val viewModel = createViewModel()
        val restock = aScriptPackageWithVariant(AGENT, variantId = "restock", variantName = "Restock").variants
        val updated = agent.copy(manifest = agent.manifest.copy(versionCode = 2), variants = agent.variants + restock)

        scriptRepository.seed(listOf(updated, plain))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Agent", viewModel.selectedPackageTitle())
        assertEquals(listOf("Agent", "Checkout", "Restock"), viewModel.entryOptions.value.map { it.name })
    }

    @Test
    fun `starting a picked variant creates an instance that runs the variant`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.pickEntry("Checkout")

            viewModel.start()

            assertEquals(ErrorAction.None, viewModel.errorAction.value)
            val instance = scriptInstanceRepository.getScriptPackageInstances().single()
            assertEquals("checkout", instance.variantId)
            assertSame(agent.variants.getValue("checkout").script, instance.entryScript)
        }

    @Test
    fun `starting the default row creates an instance without a variant`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.start()

            assertEquals(ErrorAction.None, viewModel.errorAction.value)
            val instance = scriptInstanceRepository.getScriptPackageInstances().single()
            assertEquals(AGENT, instance.definition.manifest.packageName)
            assertEquals(null, instance.variantId)
        }

    @Test
    fun `duplicating a variant instance preselects and locks its variant and creates a new instance with the same variant`() =
        runTest {
            val original = aScriptPackageInstance("original", AGENT, definition = agent, variantId = "checkout")
            val viewModel = createViewModel(duplicate = original)

            assertEquals("Checkout", viewModel.selectedEntryName())
            assertTrue(viewModel.entryLocked.value)
            viewModel.start()

            assertEquals(ErrorAction.None, viewModel.errorAction.value)
            val copy = scriptInstanceRepository.getScriptPackageInstances().single()
            assertNotEquals(original.id, copy.id)
            assertEquals("checkout", copy.variantId)
        }

    @Test
    fun `while duplicating, other packages are unlocked and returning to the original reopens its locked variant`() {
        val original = aScriptPackageInstance("original", AGENT, definition = agent, variantId = "checkout")
        val viewModel = createViewModel(duplicate = original)

        viewModel.selectRow("Plain")
        assertFalse(viewModel.entryLocked.value)

        viewModel.selectRow("Agent")
        assertEquals("Checkout", viewModel.selectedEntryName())
        assertTrue(viewModel.entryLocked.value)
    }

    @Test
    fun `a request for a variant preselects its package and that variant, unlocked`() {
        val viewModel = createViewModel(request = ScriptRequest(AGENT, variantId = "checkout"))

        assertEquals("Agent", viewModel.selectedPackageTitle())
        assertEquals("Checkout", viewModel.selectedEntryName())
        assertFalse(viewModel.entryLocked.value)
    }

    @Test
    fun `a request for an unknown variant falls back to the package's default entry`() {
        val viewModel = createViewModel(request = ScriptRequest(AGENT, variantId = "gone"))

        assertEquals("Agent", viewModel.selectedEntryName())
    }

    @Test
    fun `a request without a variant preselects the package's default entry`() {
        val viewModel = createViewModel(request = ScriptRequest(PLAIN))

        assertEquals("Plain", viewModel.selectedPackageTitle())
        assertEquals("Plain", viewModel.selectedEntryName())
    }

    @Test
    fun `without a request or duplicate the first package's default entry is selected`() {
        val viewModel = createViewModel()

        assertEquals("Agent", viewModel.selectedPackageTitle())
        assertEquals("Agent", viewModel.selectedEntryName())
    }

    private fun ScriptPackage.named(name: String) = copy(manifest = manifest.copy(name = name))

    private companion object {
        const val AGENT = "com.example.agent"
        const val PLAIN = "com.example.plain"

        fun subscription(publicId: String) =
            Subscription(
                id = "sub-$publicId",
                entitlement =
                    ScriptEntitlement(
                        publicIdentifier = publicId,
                        title = publicId,
                        latestRelease = null,
                        latestDraftRelease = null,
                        shortDescription = null,
                        price = null,
                        capacity = ScriptCapacity.None,
                    ),
            )
    }
}

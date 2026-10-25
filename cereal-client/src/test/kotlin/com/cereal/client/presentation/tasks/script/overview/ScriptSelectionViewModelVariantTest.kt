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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
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

    private val agent = aScriptPackageWithVariant(AGENT, variantId = "checkout", variantName = "Checkout")
    private val plain = aScriptPackage(PLAIN)

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
                scriptLicenseChecker = ScriptLicenseChecker(authProvider, preferences),
                applicationRepository = InMemoryApplicationRepository(),
                scriptInstanceFactory = ScriptInstanceFactory(noOpScopeLinker),
            ),
        hasNotificationChannelsConfiguredInteractor = HasNotificationChannelsConfiguredInteractor(InMemoryNotificationSettingsRepository()),
        hasLlmProviderConfigInteractor = HasLlmProviderConfigInteractor(InMemoryLlmProviderConfigRepository()),
        errorResolver = ErrorResolver(),
    ).also { dispatcher.scheduler.advanceUntilIdle() }

    @Suppress("UNCHECKED_CAST")
    private fun ScriptSelectionViewModel.rows() =
        ((overviewViewState.value as ScriptSelectionState.Overview.Filled).viewModel as GroupedListViewModel<Any?>).let { list ->
            list to list.state.value
        }

    private fun ScriptSelectionViewModel.selectedTitle() =
        rows()
            .second
            .single { it.selected }
            .content.title

    private fun ScriptSelectionViewModel.selectedRow() =
        rows()
            .second
            .single { it.selected }
            .content.id as ScriptEntryRow

    private fun ScriptSelectionViewModel.selectRow(title: String) {
        val (list, rows) = rows()
        list.onItemClick(rows.first { it.content.title == title })
        dispatcher.scheduler.advanceUntilIdle()
    }

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
    fun `lists one row per entry, the default under the package name and each variant under its own name`() {
        val viewModel = createViewModel()

        assertEquals(listOf("Test", "Checkout", "Test"), viewModel.rows().second.map { it.content.title })
    }

    @Test
    fun `choosing a variant row opens the variant's configuration form`() {
        val viewModel = createViewModel()

        viewModel.selectRow("Checkout")

        assertEquals(listOf(VARIANT_FIELD), viewModel.formFieldKeys())
    }

    @Test
    fun `starting a variant row creates an instance that runs the variant`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.selectRow("Checkout")

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
    fun `duplicating a variant instance preselects its row and creates a new instance with the same variant`() =
        runTest {
            val original = aScriptPackageInstance("original", AGENT, definition = agent, variantId = "checkout")
            val viewModel = createViewModel(duplicate = original)

            assertEquals("Checkout", viewModel.selectedTitle())
            viewModel.start()

            assertEquals(ErrorAction.None, viewModel.errorAction.value)
            val copy = scriptInstanceRepository.getScriptPackageInstances().single()
            assertNotEquals(original.id, copy.id)
            assertEquals("checkout", copy.variantId)
        }

    @Test
    fun `a request for a variant preselects that variant's row`() {
        val viewModel = createViewModel(request = ScriptRequest(AGENT, variantId = "checkout"))

        assertEquals("Checkout", viewModel.selectedTitle())
    }

    @Test
    fun `a request without a variant preselects the package's default row`() {
        val viewModel = createViewModel(request = ScriptRequest(PLAIN))

        assertEquals(ScriptEntryRow(plain), viewModel.selectedRow())
    }

    @Test
    fun `without a request or duplicate the first row is selected`() {
        val viewModel = createViewModel()

        assertEquals(ScriptEntryRow(agent), viewModel.selectedRow())
    }

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

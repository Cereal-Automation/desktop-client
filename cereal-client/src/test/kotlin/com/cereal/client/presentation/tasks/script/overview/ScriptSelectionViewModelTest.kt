package com.cereal.client.presentation.tasks.script.overview

import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.exception.ScriptSyncException
import com.cereal.client.application.interactor.notification.HasNotificationChannelsConfiguredInteractor
import com.cereal.client.application.interactor.script.GetScriptCapacityInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.script.GetSdkVersionInteractor
import com.cereal.client.application.interactor.script.NumberOfConcurrentTasksConflictException
import com.cereal.client.application.interactor.script.StartScriptInteractor
import com.cereal.client.application.interactor.script.SyncScriptsOnScriptSelectionInteractor
import com.cereal.client.application.interactor.settings.ai.HasLlmProviderConfigInteractor
import com.cereal.client.application.interactor.task.UpdateScriptPackageInstanceInteractor
import com.cereal.client.application.script.ScriptLicenseChecker
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.script.ScriptFeature
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLlmProviderConfigRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAuthProvider
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.model.LoadState
import com.cereal.client.presentation.tasks.script.overview.configuration.ScriptConfigurationViewModel
import com.cereal.client.presentation.tasks.script.overview.configuration.model.ConfigurationItemsForm
import com.cereal.client.presentation.tasks.script.overview.configuration.model.NotificationOverridesForm
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.swiftzer.semver.SemVer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

@OptIn(ExperimentalCoroutinesApi::class)
class ScriptSelectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    private val group = ScriptPackageGroup("g1", "Group 1")
    private val getScriptsInteractor: GetScriptsInteractor = mockk(relaxed = true)
    private val getSdkVersionInteractor: GetSdkVersionInteractor = mockk(relaxed = true)
    private val syncScriptsOnScriptSelectionInteractor: SyncScriptsOnScriptSelectionInteractor = mockk(relaxed = true)
    private val startScriptInteractor: StartScriptInteractor = mockk(relaxed = true)
    private val updateScriptPackageInstanceInteractor: UpdateScriptPackageInstanceInteractor = mockk(relaxed = true)
    private val hasNotificationChannelsConfiguredInteractor: HasNotificationChannelsConfiguredInteractor = mockk(relaxed = true)
    private val llmProviderConfigRepository = InMemoryLlmProviderConfigRepository()
    private val errorResolver: ErrorResolver = mockk(relaxed = true)
    private val configViewModel: ScriptConfigurationViewModel = mockk(relaxed = true)

    // Capacity is exercised through the real GetScriptCapacityInteractor + ScriptLicenseChecker over an
    // in-memory auth provider, so these tests assert observable capacity state, not interactor calls.
    private val preferences = InMemoryApplicationPreferenceRepository()
    private var subscriptions: List<Subscription> = emptyList()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { errorResolver.errorAction } returns mockk(relaxed = true)
        coEvery { getScriptsInteractor(any()) } returns flowOf(SuspendableResult.Success(emptyList()))

        // ScriptSelectionViewModel resolves ScriptConfigurationViewModel from Koin when a script is selected.
        every { configViewModel.scriptConfigurationForm } returns mutableStateOf(ConfigurationItemsForm(emptyList(), emptyMap()) { _, _, _ -> })
        every { configViewModel.notificationOverridesForm } returns NotificationOverridesForm()
        every { configViewModel.validate() } returns true
        startKoin {
            modules(
                module {
                    factory<ScriptConfigurationViewModel> { configViewModel }
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
        initialPublicIdentifier: String? = null,
        initialScriptPackageInstance: ScriptPackageInstance? = null,
        editing: Boolean = false,
    ) = ScriptSelectionViewModel(
        scope = CoroutineScope(dispatcher),
        scriptPackageGroup = group,
        initialScriptPackageInstance = initialScriptPackageInstance,
        initialRequest = initialPublicIdentifier?.let(::ScriptRequest),
        editing = editing,
        dispatcherProvider = dispatcherProvider,
        getScriptsInteractor = getScriptsInteractor,
        getScriptCapacityInteractor =
            GetScriptCapacityInteractor(
                ScriptLicenseChecker(InMemoryAuthProvider(subscriptions = subscriptions), preferences),
            ),
        getSdkVersionInteractor = getSdkVersionInteractor,
        syncScriptsOnScriptSelectionInteractor = syncScriptsOnScriptSelectionInteractor,
        startScriptInteractor = startScriptInteractor,
        updateScriptPackageInstanceInteractor = updateScriptPackageInstanceInteractor,
        hasNotificationChannelsConfiguredInteractor = hasNotificationChannelsConfiguredInteractor,
        hasLlmProviderConfigInteractor = HasLlmProviderConfigInteractor(llmProviderConfigRepository),
        errorResolver = errorResolver,
    )

    private fun stubSdkVersion(version: SemVer) {
        val callbackSlot = slot<suspend (SuspendableResult<SemVer, Exception>) -> Unit>()
        coEvery { getSdkVersionInteractor(any(), capture(callbackSlot)) } coAnswers {
            callbackSlot.captured.invoke(SuspendableResult.Success(version))
        }
    }

    private fun aScriptPackage(
        packageName: String,
        name: String,
        sdkVersion: String? = null,
        features: Set<ScriptFeature> = emptySet(),
    ) = ScriptPackage(
        source = java.io.File("/tmp/$packageName.jar"),
        manifest =
            Manifest(
                packageName = packageName,
                name = name,
                versionCode = 1L,
                sdkVersion = sdkVersion,
                features = features,
            ),
        mainScript = mockk(relaxed = true),
        childScripts = emptyMap(),
    )

    @Test
    fun `selecting a script that requires a newer client blocks launching`() {
        coEvery { getScriptsInteractor(any()) } returns
            flowOf(SuspendableResult.Success(listOf(aScriptPackage("com.example.beta", "Beta", sdkVersion = "2.0.0"))))
        stubSdkVersion(SemVer(1, 0, 0))

        val viewModel = createViewModel(initialPublicIdentifier = "com.example.beta")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.selectedScriptUpdateRequired.value)
    }

    @Test
    fun `shows the loading state, not the empty one, before the installed scripts have loaded`() {
        val viewModel = createViewModel()

        assertTrue(viewModel.overviewViewState.value is ScriptSelectionState.Overview.Loading)
    }

    @Test
    fun `loadInstalledScripts shows the empty state when there are no scripts`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.overviewViewState.value is ScriptSelectionState.Overview.Empty)
    }

    @Test
    fun `loadInstalledScripts shows the filled state and selects the first script when several exist`() {
        val first = aScriptPackage("com.example.a", "A")
        coEvery { getScriptsInteractor(any()) } returns
            flowOf(SuspendableResult.Success(listOf(first, aScriptPackage("com.example.b", "B"))))
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val overview = viewModel.overviewViewState.value as ScriptSelectionState.Overview.Filled
        assertEquals(first, overview.viewModel.selectedItem)
        assertTrue(viewModel.configurationViewState.value is ScriptSelectionState.Config.Selected)
    }

    @Test
    fun `loadInstalledScripts selects the requested script over the first one`() {
        val second = aScriptPackage("com.example.b", "B")
        coEvery { getScriptsInteractor(any()) } returns
            flowOf(SuspendableResult.Success(listOf(aScriptPackage("com.example.a", "A"), second)))
        val viewModel = createViewModel(initialPublicIdentifier = "com.example.b")
        dispatcher.scheduler.advanceUntilIdle()

        val overview = viewModel.overviewViewState.value as ScriptSelectionState.Overview.Filled
        assertEquals(second, overview.viewModel.selectedItem)
    }

    @Test
    fun `loadInstalledScripts hides the list and selects the script when exactly one exists`() {
        coEvery { getScriptsInteractor(any()) } returns
            flowOf(SuspendableResult.Success(listOf(aScriptPackage("com.example.a", "A"))))
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.overviewViewState.value is ScriptSelectionState.Overview.Single)
        assertTrue(viewModel.configurationViewState.value is ScriptSelectionState.Config.Selected)
    }

    @Test
    fun `refreshScriptList sets success state on success`() {
        coEvery { syncScriptsOnScriptSelectionInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(SuspendableResult.Success(Unit))
        }
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.refreshScriptList()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.scriptRefreshState.value
        assertTrue(state is LoadState.NotLoading && state.success)
    }

    @Test
    fun `refreshScriptList sets error state on sync exception`() {
        coEvery { syncScriptsOnScriptSelectionInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(
                SuspendableResult.Failure(ScriptSyncException(mapOf("pkg" to RuntimeException("x")))),
            )
        }
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.refreshScriptList()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.scriptRefreshState.value is LoadState.Error)
    }

    @Test
    fun `refreshScriptList sets error state on generic failure`() {
        coEvery { syncScriptsOnScriptSelectionInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(SuspendableResult.Failure(RuntimeException("boom")))
        }
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.refreshScriptList()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.scriptRefreshState.value is LoadState.Error)
    }

    @Test
    fun `launchScript shows the no-notification-channels warning when none are configured`() {
        coEvery { hasNotificationChannelsConfiguredInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Boolean, Exception>) -> Unit>()(SuspendableResult.Success(false))
        }
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.launchScript()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.showNoNotificationChannelsWarning.value)
    }

    @Test
    fun `onNoNotificationChannelsWarningDismissed hides the warning`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onNoNotificationChannelsWarningDismissed()

        assertFalse(viewModel.showNoNotificationChannelsWarning.value)
    }

    @Test
    fun `onNoNotificationChannelsWarningAccepted hides the warning`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onNoNotificationChannelsWarningAccepted()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.showNoNotificationChannelsWarning.value)
    }

    private fun stubNotificationChannelsConfigured(configured: Boolean) {
        coEvery { hasNotificationChannelsConfiguredInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Boolean, Exception>) -> Unit>()(SuspendableResult.Success(configured))
        }
    }

    private fun stubStartScriptSucceeds() {
        coEvery { startScriptInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<ScriptPackageInstance, Exception>) -> Unit>()(
                SuspendableResult.Success(mockk<ScriptPackageInstance>(relaxed = true)),
            )
        }
    }

    /** Creates the view model with one script selected and advances until its configuration is loaded. */
    private fun createViewModelWithSelectedScript(features: Set<ScriptFeature>): ScriptSelectionViewModel {
        coEvery { getScriptsInteractor(any()) } returns
            flowOf(SuspendableResult.Success(listOf(aScriptPackage("com.example.ai", "AI", features = features))))
        val viewModel = createViewModel(initialPublicIdentifier = "com.example.ai")
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `saving an edit asks to confirm a concurrency conflict, then saves the setup accepting it`() {
        val setup = fixtures.aScriptPackageInstance("setup-1", "com.example.ai")
        coEvery { getScriptsInteractor(any()) } returns flowOf(SuspendableResult.Success(listOf(aScriptPackage("com.example.ai", "AI"))))
        val saved = slot<UpdateScriptPackageInstanceInteractor.Params>()
        coEvery { updateScriptPackageInstanceInteractor(capture(saved), any()) } coAnswers {
            val result: SuspendableResult<ScriptPackageInstance, Exception> =
                if (firstArg<UpdateScriptPackageInstanceInteractor.Params>().ignoreConcurrencyConflicts) {
                    SuspendableResult.Success(setup)
                } else {
                    SuspendableResult.Failure(NumberOfConcurrentTasksConflictException(setup.numberOfConcurrentTasks, emptyList(), null))
                }
            secondArg<suspend (SuspendableResult<ScriptPackageInstance, Exception>) -> Unit>()(result)
        }
        val viewModel = createViewModel(initialScriptPackageInstance = setup, editing = true)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.saveEdit()
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.startScriptWarningConfirmation.value)
        assertNull(viewModel.scriptInstanceStarted.value)

        viewModel.onStartScriptWarningConfirmationAccepted()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("setup-1", saved.captured.scriptPackageInstance.id)
        assertEquals(setup, viewModel.scriptInstanceStarted.value)
        coVerify(exactly = 0) { startScriptInteractor(any(), any()) }
    }

    private fun launch(viewModel: ScriptSelectionViewModel) {
        viewModel.launchScript()
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `launchScript shows the AI warning and starts no task when an llm script has no provider set up`() {
        stubNotificationChannelsConfigured(true)
        stubStartScriptSucceeds()
        val viewModel = createViewModelWithSelectedScript(setOf(ScriptFeature.LLM))

        launch(viewModel)

        assertTrue(viewModel.showNoAiProviderWarning.value)
        coVerify(exactly = 0) { startScriptInteractor(any(), any()) }
    }

    @Test
    fun `onNoAiProviderWarningAccepted starts the task when notifications are set up`() {
        stubNotificationChannelsConfigured(true)
        stubStartScriptSucceeds()
        val viewModel = createViewModelWithSelectedScript(setOf(ScriptFeature.LLM))
        launch(viewModel)

        viewModel.onNoAiProviderWarningAccepted()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.showNoAiProviderWarning.value)
        coVerify(exactly = 1) { startScriptInteractor(any(), any()) }
        assertNotNull(viewModel.scriptInstanceStarted.value)
    }

    @Test
    fun `onNoAiProviderWarningAccepted shows the notification warning next when notifications are unset`() {
        stubNotificationChannelsConfigured(false)
        stubStartScriptSucceeds()
        val viewModel = createViewModelWithSelectedScript(setOf(ScriptFeature.LLM))
        launch(viewModel)

        viewModel.onNoAiProviderWarningAccepted()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.showNoAiProviderWarning.value)
        assertTrue(viewModel.showNoNotificationChannelsWarning.value)
        coVerify(exactly = 0) { startScriptInteractor(any(), any()) }
    }

    @Test
    fun `onNoAiProviderWarningDismissed hides the warning, starts no task and keeps the form`() {
        stubNotificationChannelsConfigured(true)
        stubStartScriptSucceeds()
        val viewModel = createViewModelWithSelectedScript(setOf(ScriptFeature.LLM))
        launch(viewModel)

        viewModel.onNoAiProviderWarningDismissed()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.showNoAiProviderWarning.value)
        coVerify(exactly = 0) { startScriptInteractor(any(), any()) }
        assertTrue(viewModel.configurationViewState.value is ScriptSelectionState.Config.Selected)
    }

    @Test
    fun `launchScript shows no AI warning when an llm script has a provider saved`() =
        runTest {
            llmProviderConfigRepository.save(
                LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, "http://localhost:11434/v1", "", "llama3"),
            )
            stubNotificationChannelsConfigured(true)
            stubStartScriptSucceeds()
            val viewModel = createViewModelWithSelectedScript(setOf(ScriptFeature.LLM))

            launch(viewModel)

            assertFalse(viewModel.showNoAiProviderWarning.value)
            coVerify(exactly = 1) { startScriptInteractor(any(), any()) }
        }

    @Test
    fun `launchScript shows no AI warning for a script without llm and no provider saved`() {
        stubNotificationChannelsConfigured(true)
        stubStartScriptSucceeds()
        val viewModel = createViewModelWithSelectedScript(emptySet())

        launch(viewModel)

        assertFalse(viewModel.showNoAiProviderWarning.value)
        coVerify(exactly = 1) { startScriptInteractor(any(), any()) }
    }

    @Test
    fun `onStartScriptWarningConfirmationDenied clears the confirmation`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStartScriptWarningConfirmationDenied()

        assertNull(viewModel.startScriptWarningConfirmation.value)
    }

    @Test
    fun `validate returns false when no script is configured`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.validate())
    }

    @Test
    fun `auto-selecting the initial script configures the selection`() {
        coEvery { getScriptsInteractor(any()) } returns
            flowOf(SuspendableResult.Success(listOf(mockk<ScriptPackage>(relaxed = true))))
        val viewModel = createViewModel(initialPublicIdentifier = "")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.configurationViewState.value is ScriptSelectionState.Config.Selected)
        assertTrue(viewModel.validate())
    }

    @Test
    fun `launchScript starts the script when notification channels are configured`() {
        coEvery { getScriptsInteractor(any()) } returns
            flowOf(SuspendableResult.Success(listOf(mockk<ScriptPackage>(relaxed = true))))
        coEvery { hasNotificationChannelsConfiguredInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Boolean, Exception>) -> Unit>()(SuspendableResult.Success(true))
        }
        coEvery { startScriptInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<ScriptPackageInstance, Exception>) -> Unit>()(
                SuspendableResult.Success(mockk<ScriptPackageInstance>(relaxed = true)),
            )
        }
        val viewModel = createViewModel(initialPublicIdentifier = "")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.launchScript()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { startScriptInteractor(any(), any()) }
        assertNotNull(viewModel.scriptInstanceStarted.value)
    }

    @Test
    fun `onStartScriptWarningConfirmationAccepted launches the script ignoring conflicts`() {
        coEvery { getScriptsInteractor(any()) } returns
            flowOf(SuspendableResult.Success(listOf(mockk<ScriptPackage>(relaxed = true))))
        coEvery { startScriptInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<ScriptPackageInstance, Exception>) -> Unit>()(
                SuspendableResult.Success(mockk<ScriptPackageInstance>(relaxed = true)),
            )
        }
        val viewModel = createViewModel(initialPublicIdentifier = "")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStartScriptWarningConfirmationAccepted()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.startScriptWarningConfirmation.value)
        coVerify(exactly = 1) { startScriptInteractor(any(), any()) }
    }

    @Test
    fun `launchScript reports an error when starting the script fails`() {
        coEvery { getScriptsInteractor(any()) } returns
            flowOf(SuspendableResult.Success(listOf(mockk<ScriptPackage>(relaxed = true))))
        coEvery { hasNotificationChannelsConfiguredInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Boolean, Exception>) -> Unit>()(SuspendableResult.Success(true))
        }
        coEvery { startScriptInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<ScriptPackageInstance, Exception>) -> Unit>()(
                SuspendableResult.Failure(RuntimeException("boom")),
            )
        }
        val viewModel = createViewModel(initialPublicIdentifier = "")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.launchScript()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { errorResolver.setError(any<Exception>()) }
    }

    @Test
    fun `selecting an entitled script exposes its finite record capacity`() {
        subscriptions = listOf(subscription(SCRIPT_ID, ScriptCapacity.Limited(100, "records")))
        coEvery { getScriptsInteractor(any()) } returns
            flowOf(SuspendableResult.Success(listOf(aScriptPackage(SCRIPT_ID, "Script"))))

        val viewModel = createViewModel(initialPublicIdentifier = SCRIPT_ID)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ScriptCapacity.Limited(100, "records"), viewModel.selectedScriptCapacity.value)
    }

    @Test
    fun `selecting an entitled script exposes an unlimited record capacity`() {
        subscriptions = listOf(subscription(SCRIPT_ID, ScriptCapacity.Unlimited("records")))
        coEvery { getScriptsInteractor(any()) } returns
            flowOf(SuspendableResult.Success(listOf(aScriptPackage(SCRIPT_ID, "Script"))))

        val viewModel = createViewModel(initialPublicIdentifier = SCRIPT_ID)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ScriptCapacity.Unlimited("records"), viewModel.selectedScriptCapacity.value)
    }

    @Test
    fun `capacity is None when the selected script has no capacity concept`() {
        subscriptions = listOf(subscription(SCRIPT_ID, ScriptCapacity.None))
        coEvery { getScriptsInteractor(any()) } returns
            flowOf(SuspendableResult.Success(listOf(aScriptPackage(SCRIPT_ID, "Script"))))

        val viewModel = createViewModel(initialPublicIdentifier = SCRIPT_ID)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ScriptCapacity.None, viewModel.selectedScriptCapacity.value)
    }

    @Test
    fun `capacity is None when the selected script is not entitled`() {
        // No subscription for the selected script, so it is unlicensed and capacity must not be read.
        coEvery { getScriptsInteractor(any()) } returns
            flowOf(SuspendableResult.Success(listOf(aScriptPackage(SCRIPT_ID, "Script"))))

        val viewModel = createViewModel(initialPublicIdentifier = SCRIPT_ID)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ScriptCapacity.None, viewModel.selectedScriptCapacity.value)
    }

    private companion object {
        private const val SCRIPT_ID = "com.example.script"

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

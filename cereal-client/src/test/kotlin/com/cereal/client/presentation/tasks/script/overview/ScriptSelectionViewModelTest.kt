package com.cereal.client.presentation.tasks.script.overview

import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.exception.ScriptSyncException
import com.cereal.client.application.interactor.notification.HasNotificationChannelsConfiguredInteractor
import com.cereal.client.application.interactor.script.GetScriptCapacityInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.script.GetSdkVersionInteractor
import com.cereal.client.application.interactor.script.StartScriptInteractor
import com.cereal.client.application.interactor.script.SyncScriptsOnScriptSelectionInteractor
import com.cereal.client.application.script.ScriptLicenseChecker
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
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
    private val hasNotificationChannelsConfiguredInteractor: HasNotificationChannelsConfiguredInteractor = mockk(relaxed = true)
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

    private fun createViewModel(initialPublicIdentifier: String? = null) =
        ScriptSelectionViewModel(
            scope = CoroutineScope(dispatcher),
            scriptPackageGroup = group,
            initialScriptPackageInstance = null,
            initialPublicIdentifier = initialPublicIdentifier,
            dispatcherProvider = dispatcherProvider,
            getScriptsInteractor = getScriptsInteractor,
            getScriptCapacityInteractor =
                GetScriptCapacityInteractor(
                    ScriptLicenseChecker(InMemoryAuthProvider(subscriptions = subscriptions), preferences),
                ),
            getSdkVersionInteractor = getSdkVersionInteractor,
            syncScriptsOnScriptSelectionInteractor = syncScriptsOnScriptSelectionInteractor,
            startScriptInteractor = startScriptInteractor,
            hasNotificationChannelsConfiguredInteractor = hasNotificationChannelsConfiguredInteractor,
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
    ) = ScriptPackage(
        source = java.io.File("/tmp/$packageName.jar"),
        manifest =
            Manifest(
                packageName = packageName,
                name = name,
                versionCode = 1L,
                sdkVersion = sdkVersion,
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
    fun `loadInstalledScripts shows the empty state when there are no scripts`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.overviewViewState.value is ScriptSelectionState.Overview.Empty)
    }

    @Test
    fun `loadInstalledScripts shows the filled state when scripts exist`() {
        coEvery { getScriptsInteractor(any()) } returns
            flowOf(SuspendableResult.Success(listOf(mockk<ScriptPackage>(relaxed = true))))
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.overviewViewState.value is ScriptSelectionState.Overview.Filled)
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

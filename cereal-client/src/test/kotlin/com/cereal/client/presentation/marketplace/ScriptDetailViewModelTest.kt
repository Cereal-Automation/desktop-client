package com.cereal.client.presentation.marketplace

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.exception.ClientUpdateRequiredException
import com.cereal.client.application.interactor.auth.GetAuthenticatedUserInteractor
import com.cereal.client.application.interactor.marketplace.HasRunningTasksForScriptInteractor
import com.cereal.client.application.interactor.marketplace.InstallMarketplaceScriptInteractor
import com.cereal.client.application.interactor.marketplace.IsScriptInstalledInteractor
import com.cereal.client.application.interactor.marketplace.RemoveMarketplaceScriptInteractor
import com.cereal.client.application.interactor.settings.OpenUrlInteractor
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.user.User
import com.cereal.client.domain.provider.CheckoutCancelledException
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.presentation.error.ErrorResolver
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScriptDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    private val installMarketplaceScriptInteractor: InstallMarketplaceScriptInteractor = mockk(relaxed = true)
    private val isScriptInstalledInteractor: IsScriptInstalledInteractor = mockk(relaxed = true)
    private val openUrlInteractor: OpenUrlInteractor = mockk(relaxed = true)
    private val getAuthenticatedUserInteractor: GetAuthenticatedUserInteractor = mockk(relaxed = true)
    private val errorResolver: ErrorResolver = mockk(relaxed = true)
    private val removeMarketplaceScriptInteractor: RemoveMarketplaceScriptInteractor = mockk(relaxed = true)
    private val hasRunningTasksForScriptInteractor: HasRunningTasksForScriptInteractor = mockk(relaxed = true)
    private val scriptRepository: ScriptRepository = mockk(relaxed = true)

    private val startedInstances = mutableListOf<String>()

    private fun user(isGuest: Boolean) = User(id = "1", name = "n", email = "e", encryptionKey = "k", accessToken = "t", isGuest = isGuest)

    private fun script(isFree: Boolean = true) = MarketplaceScript(id = "1", publicIdentifier = "pkg.id", title = "Title", isFree = isFree)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { errorResolver.errorAction } returns mockk(relaxed = true)
        coEvery { getAuthenticatedUserInteractor(any()) } returns flowOf(SuspendableResult.Success(user(isGuest = false)))
        coEvery { isScriptInstalledInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Boolean, Exception>) -> Unit>()(SuspendableResult.Success(false))
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        ScriptDetailViewModel(
            scope = CoroutineScope(dispatcher),
            dispatcherProvider = dispatcherProvider,
            installMarketplaceScriptInteractor = installMarketplaceScriptInteractor,
            isScriptInstalledInteractor = isScriptInstalledInteractor,
            openUrlInteractor = openUrlInteractor,
            getAuthenticatedUserInteractor = getAuthenticatedUserInteractor,
            errorResolver = errorResolver,
            removeMarketplaceScriptInteractor = removeMarketplaceScriptInteractor,
            hasRunningTasksForScriptInteractor = hasRunningTasksForScriptInteractor,
            scriptRepository = scriptRepository,
            onStartNewInstance = { startedInstances.add(it) },
        )

    @Test
    fun `onScriptLoaded leaves state Idle for a non-installed script`() {
        val viewModel = createViewModel()

        viewModel.onScriptLoaded(script())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ScriptDetailViewModel.InstallState.Idle, viewModel.installState.value)
    }

    @Test
    fun `onScriptLoaded requires purchase for a guest viewing a paid script`() {
        coEvery { getAuthenticatedUserInteractor(any()) } returns flowOf(SuspendableResult.Success(user(isGuest = true)))
        val viewModel = createViewModel()

        viewModel.onScriptLoaded(script(isFree = false))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ScriptDetailViewModel.InstallState.GuestPurchaseRequired, viewModel.installState.value)
    }

    @Test
    fun `onScriptLoaded reflects an already installed script and its running tasks`() {
        coEvery { isScriptInstalledInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Boolean, Exception>) -> Unit>()(SuspendableResult.Success(true))
        }
        val pkg = mockk<ScriptPackage>(relaxed = true)
        coEvery { scriptRepository.getScript(any()) } returns pkg
        coEvery { hasRunningTasksForScriptInteractor(any()) } returns flowOf(SuspendableResult.Success(true))

        val viewModel = createViewModel()
        viewModel.onScriptLoaded(script())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ScriptDetailViewModel.InstallState.AlreadyInstalled, viewModel.installState.value)
        assertTrue(viewModel.hasRunningTasks.value)
    }

    @Test
    fun `onInstall of a free script transitions to Success`() {
        coEvery { installMarketplaceScriptInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(SuspendableResult.Success(Unit))
        }
        val viewModel = createViewModel()

        viewModel.onInstall(script(isFree = true))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ScriptDetailViewModel.InstallState.Success, viewModel.installState.value)
    }

    @Test
    fun `onInstall returns to Idle when checkout is cancelled`() {
        coEvery { installMarketplaceScriptInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(SuspendableResult.Failure(CheckoutCancelledException()))
        }
        val viewModel = createViewModel()

        viewModel.onInstall(script(isFree = false))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ScriptDetailViewModel.InstallState.Idle, viewModel.installState.value)
    }

    @Test
    fun `onInstall reports the error for non-cancellation failures`() {
        coEvery { installMarketplaceScriptInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(SuspendableResult.Failure(RuntimeException("boom")))
        }
        val viewModel = createViewModel()

        viewModel.onInstall(script(isFree = true))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ScriptDetailViewModel.InstallState.Idle, viewModel.installState.value)
        coVerify { errorResolver.setError(any<Exception>()) }
    }

    @Test
    fun `onInstall shows an inline update-required message when the client is too old`() {
        coEvery { installMarketplaceScriptInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(
                SuspendableResult.Failure(ClientUpdateRequiredException("2.0.0")),
            )
        }
        val viewModel = createViewModel()

        viewModel.onInstall(script(isFree = true))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.installState.value is ScriptDetailViewModel.InstallState.Error)
        coVerify(exactly = 0) { errorResolver.setError(any<Exception>()) }
    }

    @Test
    fun `onOpenUrl opens the given url`() {
        val viewModel = createViewModel()

        viewModel.onOpenUrl("https://example.com")
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { openUrlInteractor(any(), any()) }
    }

    @Test
    fun `onStartNewInstance forwards the loaded script identifier`() {
        val viewModel = createViewModel()
        viewModel.onScriptLoaded(script())
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStartNewInstance()

        assertEquals(listOf("pkg.id"), startedInstances)
    }

    @Test
    fun `remove dialog visibility toggles`() {
        val viewModel = createViewModel()

        viewModel.onRemoveClicked()
        assertTrue(viewModel.confirmRemoveDialog.value)

        viewModel.onRemoveDismissed()
        assertFalse(viewModel.confirmRemoveDialog.value)
    }

    @Test
    fun `onRemoveConfirmed without a loaded package reports an error`() {
        val viewModel = createViewModel()

        viewModel.onRemoveConfirmed()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.confirmRemoveDialog.value)
        coVerify { errorResolver.setError(any<Exception>()) }
    }

    @Test
    fun `onRemoveConfirmed removes an installed package and returns to Idle`() {
        coEvery { isScriptInstalledInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Boolean, Exception>) -> Unit>()(SuspendableResult.Success(true))
        }
        val pkg = mockk<ScriptPackage>(relaxed = true)
        coEvery { scriptRepository.getScript(any()) } returns pkg
        coEvery { hasRunningTasksForScriptInteractor(any()) } returns flowOf(SuspendableResult.Success(false))
        coEvery { removeMarketplaceScriptInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<Unit, Exception>) -> Unit>()(SuspendableResult.Success(Unit))
        }
        val viewModel = createViewModel()
        viewModel.onScriptLoaded(script())
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onRemoveConfirmed()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ScriptDetailViewModel.InstallState.Idle, viewModel.installState.value)
    }
}

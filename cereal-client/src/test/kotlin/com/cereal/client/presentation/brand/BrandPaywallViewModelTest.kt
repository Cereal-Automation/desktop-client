package com.cereal.client.presentation.brand

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.auth.LogoutInteractor
import com.cereal.client.application.interactor.brand.BrandGateStatus
import com.cereal.client.application.interactor.brand.GetBrandGateStatusInteractor
import com.cereal.client.application.interactor.brand.SyncBrandScriptsInteractor
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrandPaywallViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    private val applicationConfig: ApplicationConfig = mockk(relaxed = true)
    private val getBrandGateStatusInteractor: GetBrandGateStatusInteractor = mockk(relaxed = true)
    private val syncBrandScriptsInteractor: SyncBrandScriptsInteractor = mockk(relaxed = true)
    private val logoutInteractor: LogoutInteractor = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stubStatus(status: BrandGateStatus) {
        coEvery { getBrandGateStatusInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<BrandGateStatus, Exception>) -> Unit>()
                .invoke(SuspendableResult.Success(status))
        }
    }

    private fun createViewModel() =
        BrandPaywallViewModel(
            scope = CoroutineScope(dispatcher),
            dispatcherProvider = dispatcherProvider,
            applicationConfig = applicationConfig,
            getBrandGateStatusInteractor = getBrandGateStatusInteractor,
            syncBrandScriptsInteractor = syncBrandScriptsInteractor,
            logoutInteractor = logoutInteractor,
        )

    @Test
    fun `stock build is entitled without evaluating the gate`() {
        every { applicationConfig.isBranded } returns false
        val viewModel = createViewModel()

        viewModel.check()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(BrandEntitlementViewState.Entitled, viewModel.state.value)
        coVerify(exactly = 0) { getBrandGateStatusInteractor(any(), any()) }
    }

    @Test
    fun `missing subscription locks the gate`() {
        every { applicationConfig.isBranded } returns true
        stubStatus(BrandGateStatus.NeedsSubscription)
        val viewModel = createViewModel()

        viewModel.check()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(BrandEntitlementViewState.Locked, viewModel.state.value)
    }

    @Test
    fun `entitled but uninstalled scripts mark the gate unavailable`() {
        every { applicationConfig.isBranded } returns true
        stubStatus(BrandGateStatus.ScriptsUnavailable)
        val viewModel = createViewModel()

        viewModel.check()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(BrandEntitlementViewState.Unavailable, viewModel.state.value)
    }

    @Test
    fun `ready status entitles the gate`() {
        every { applicationConfig.isBranded } returns true
        stubStatus(BrandGateStatus.Ready)
        val viewModel = createViewModel()

        viewModel.check()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(BrandEntitlementViewState.Entitled, viewModel.state.value)
    }

    @Test
    fun `a failed evaluation surfaces the error state`() {
        every { applicationConfig.isBranded } returns true
        coEvery { getBrandGateStatusInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<BrandGateStatus, Exception>) -> Unit>()
                .invoke(SuspendableResult.Failure(RuntimeException("network")))
        }
        val viewModel = createViewModel()

        viewModel.check()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(BrandEntitlementViewState.Error, viewModel.state.value)
    }

    @Test
    fun `retryDownload re-syncs then re-evaluates to entitled`() {
        every { applicationConfig.isBranded } returns true
        stubStatus(BrandGateStatus.Ready)
        val viewModel = createViewModel()

        viewModel.retryDownload()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { syncBrandScriptsInteractor(any(), any()) }
        assertEquals(BrandEntitlementViewState.Entitled, viewModel.state.value)
    }
}

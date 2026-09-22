package com.cereal.client.presentation.authenticate.components

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.settings.OpenUrlInteractor
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
class AuthenticationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    private val openUrlInteractor: OpenUrlInteractor = mockk(relaxed = true)
    private val applicationConfig: ApplicationConfig = mockk(relaxed = true)

    private lateinit var viewModel: AuthenticationViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { applicationConfig.marketplaceSearchScriptsUrl } returns "https://market.test"
        every { applicationConfig.privacyPolicyUrl } returns "https://privacy.test"
        every { applicationConfig.discordUrl } returns "https://discord.test"
        every { applicationConfig.statusUrl } returns "https://status.test"
        viewModel = AuthenticationViewModel(CoroutineScope(dispatcher), dispatcherProvider, openUrlInteractor, applicationConfig)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun capturedUrl(): String {
        val params = slot<OpenUrlInteractor.Params>()
        coVerify(exactly = 1) { openUrlInteractor(capture(params), any()) }
        return params.captured.url
    }

    @Test
    fun `marketplace opens the marketplace url`() {
        viewModel.marketplace()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("https://market.test", capturedUrl())
    }

    @Test
    fun `privacyPolicy opens the privacy policy url`() {
        viewModel.privacyPolicy()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("https://privacy.test", capturedUrl())
    }

    @Test
    fun `support opens the discord url`() {
        viewModel.support()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("https://discord.test", capturedUrl())
    }

    @Test
    fun `status opens the status url`() {
        viewModel.status()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("https://status.test", capturedUrl())
    }
}

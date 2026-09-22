package com.cereal.client.infrastructure.provider

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.provider.CheckoutCancelledException
import com.cereal.client.infrastructure.data.datasource.os.BrowserDataSource
import dev.kdriver.cdp.domain.Page
import dev.kdriver.cdp.domain.page
import dev.kdriver.core.browser.Browser
import dev.kdriver.core.browser.createBrowser
import dev.kdriver.core.tab.Tab
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.sentry.Sentry
import io.sentry.protocol.SentryId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class CheckoutProviderImplTest {
    private val checkoutUrl = "https://market.example.com/checkout"
    private val successUrl = "https://market.example.com/subscribed"
    private val cancelledUrl = "https://market.example.com/cancel"

    private lateinit var applicationConfig: ApplicationConfig
    private lateinit var browserDataSource: BrowserDataSource
    private lateinit var repository: CheckoutProviderImpl

    @BeforeEach
    fun setUp() {
        applicationConfig = mockk(relaxed = true)
        every { applicationConfig.marketplaceBaseUrl } returns "https://market.example.com"

        browserDataSource = mockk(relaxed = true)

        // createBrowser is a top-level suspend function in dev.kdriver.core.browser.ExtensionsKt
        mockkStatic("dev.kdriver.core.browser.ExtensionsKt")
        // tab.page is a static extension property in dev.kdriver.cdp.domain.PageKt
        mockkStatic("dev.kdriver.cdp.domain.PageKt")
        // Mock the static Sentry facade to avoid side effects on the fallback path.
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any<Throwable>()) } returns SentryId.EMPTY_ID

        repository = CheckoutProviderImpl(applicationConfig, browserDataSource)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun browserWithFrame(frameUrl: String): Browser {
        val browser = mockk<Browser>(relaxed = true)
        val tab = mockk<Tab>(relaxed = true)
        val page = mockk<Page>(relaxed = true)
        val event = mockk<Page.FrameNavigatedParameter>(relaxed = true)
        val frame = mockk<Page.Frame>(relaxed = true)

        coEvery { browser.get(any(), any(), any()) } returns tab
        every { tab.page } returns page
        every { page.frameNavigated } returns flowOf(event)
        every { event.frame } returns frame
        every { frame.parentId } returns null
        every { frame.url } returns frameUrl
        return browser
    }

    @Test
    fun `createBrowser failure falls back to desktop browse`() =
        runTest {
            coEvery {
                createBrowser(coroutineScope = any(), headless = any())
            } throws RuntimeException("boom")

            repository.awaitCheckout(checkoutUrl)

            coVerify(exactly = 1) { browserDataSource.attemptDesktopBrowse(checkoutUrl) }
        }

    @Test
    fun `cancelled exception from createBrowser is rethrown without fallback`() =
        runTest {
            coEvery {
                createBrowser(coroutineScope = any(), headless = any())
            } throws CheckoutCancelledException()

            assertFailsWith<CheckoutCancelledException> {
                repository.awaitCheckout(checkoutUrl)
            }

            coVerify(exactly = 0) { browserDataSource.attemptDesktopBrowse(any()) }
        }

    @Test
    fun `retry exhaustion falls back to desktop browse and stops browser`() =
        runTest {
            val browser = mockk<Browser>(relaxed = true)
            coEvery { browser.get(any(), any(), any()) } throws NoSuchElementException()
            coEvery {
                createBrowser(coroutineScope = any(), headless = any())
            } returns browser

            repository.awaitCheckout(checkoutUrl)

            coVerify(exactly = 1) { browser.stop() }
            coVerify(exactly = 1) { browserDataSource.attemptDesktopBrowse(checkoutUrl) }
        }

    @Test
    fun `successful redirect completes without fallback and stops browser`() =
        runTest {
            val browser = browserWithFrame(successUrl)
            coEvery {
                createBrowser(coroutineScope = any(), headless = any())
            } returns browser

            repository.awaitCheckout(checkoutUrl)

            coVerify(exactly = 1) { browser.stop() }
            coVerify(exactly = 0) { browserDataSource.attemptDesktopBrowse(any()) }
        }

    @Test
    fun `marketplace frame that is not success throws cancelled and stops browser`() =
        runTest {
            val browser = browserWithFrame(cancelledUrl)
            coEvery {
                createBrowser(coroutineScope = any(), headless = any())
            } returns browser

            assertFailsWith<CheckoutCancelledException> {
                repository.awaitCheckout(checkoutUrl)
            }

            coVerify(exactly = 1) { browser.stop() }
            coVerify(exactly = 0) { browserDataSource.attemptDesktopBrowse(any()) }
        }
}

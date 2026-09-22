package com.cereal.client.application.interactor.marketplace

import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.marketplace.ScriptSubscriptionResult
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.provider.CheckoutProvider
import com.cereal.client.domain.provider.MarketplaceProvider
import com.cereal.client.domain.provider.ScriptInstallProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

/**
 * Mock-based by design: these cases hinge on repository responses the in-memory implementations
 * can't produce — `InMemoryMarketplaceProvider.subscribeToScript` always returns Subscribed (so
 * the CheckoutRequired path can't occur), `InMemoryCheckoutProvider` is a no-op (so "awaited
 * checkout" can't be observed), and `InMemoryScriptInstallProvider.installScript` throws by design.
 * Configurable mocks are required to drive these branches.
 */
class InstallMarketplaceScriptInteractorTest {
    private val scriptInstallProvider = mockk<ScriptInstallProvider>()
    private val marketplaceRepository = mockk<MarketplaceProvider>()
    private val checkoutRepository = mockk<CheckoutProvider>(relaxed = true)
    private lateinit var interactor: InstallMarketplaceScriptInteractor

    @BeforeEach
    fun setUp() {
        interactor =
            InstallMarketplaceScriptInteractor(
                scriptInstallProvider = scriptInstallProvider,
                marketplaceRepository = marketplaceRepository,
                checkoutRepository = checkoutRepository,
            )
    }

    @Test
    fun `run installs script directly when subscription returns Subscribed`() =
        runTest {
            val script =
                aMarketplaceScript(
                    publicIdentifier = "com.example.script",
                    latestRelease = aRelease(versionName = "1.0", versionCode = 1L),
                )
            val installedPackage = aScriptPackage("com.example.script")

            coEvery { marketplaceRepository.subscribeToScript("com.example.script") } returns
                ScriptSubscriptionResult.Subscribed
            coEvery {
                scriptInstallProvider.installScript(
                    packageName = "com.example.script",
                    release = Release(versionName = "1.0", versionCode = 1L, releaseNotes = null),
                )
            } returns installedPackage

            val result = interactor.run(InstallMarketplaceScriptInteractor.Params(script))

            assertEquals(installedPackage, result)
            coVerify(exactly = 0) { checkoutRepository.awaitCheckout(any()) }
        }

    @Test
    fun `run installs script directly when subscription returns AlreadySubscribed`() =
        runTest {
            val script =
                aMarketplaceScript(
                    publicIdentifier = "com.example.script",
                    latestRelease = aRelease(versionName = "2.0", versionCode = 2L),
                )
            val installedPackage = aScriptPackage("com.example.script")

            coEvery { marketplaceRepository.subscribeToScript("com.example.script") } returns
                ScriptSubscriptionResult.AlreadySubscribed
            coEvery { scriptInstallProvider.installScript(any(), any()) } returns installedPackage

            val result = interactor.run(InstallMarketplaceScriptInteractor.Params(script))

            assertEquals(installedPackage, result)
            coVerify(exactly = 0) { checkoutRepository.awaitCheckout(any()) }
        }

    @Test
    fun `run awaits checkout before installing when subscription requires checkout`() =
        runTest {
            val checkoutUrl = "https://checkout.example.com/pay"
            val script =
                aMarketplaceScript(
                    publicIdentifier = "com.example.paid-script",
                    latestRelease = aRelease(versionName = "1.0", versionCode = 1L),
                )
            val installedPackage = aScriptPackage("com.example.paid-script")

            coEvery { marketplaceRepository.subscribeToScript("com.example.paid-script") } returns
                ScriptSubscriptionResult.CheckoutRequired(checkoutUrl)
            coEvery { scriptInstallProvider.installScript(any(), any()) } returns installedPackage

            val result = interactor.run(InstallMarketplaceScriptInteractor.Params(script))

            assertEquals(installedPackage, result)
            coVerify(exactly = 1) { checkoutRepository.awaitCheckout(checkoutUrl) }
        }

    @Test
    fun `run installs script with the release carried by the marketplace script`() =
        runTest {
            val release = aRelease(versionName = "3.1", versionCode = 31L, releaseNotes = "Bug fixes")
            val script =
                aMarketplaceScript(
                    publicIdentifier = "com.example.script",
                    latestRelease = release,
                )
            val installedPackage = aScriptPackage("com.example.script")

            coEvery { marketplaceRepository.subscribeToScript(any()) } returns
                ScriptSubscriptionResult.Subscribed
            coEvery {
                scriptInstallProvider.installScript(
                    packageName = "com.example.script",
                    release = Release(versionName = "3.1", versionCode = 31L, releaseNotes = "Bug fixes"),
                )
            } returns installedPackage

            val result = interactor.run(InstallMarketplaceScriptInteractor.Params(script))

            assertEquals(installedPackage, result)
        }

    @Test
    fun `run throws when script has no latest release`() =
        runTest {
            val script =
                aMarketplaceScript(
                    title = "Broken Script",
                    latestRelease = null,
                )

            assertThrows<IllegalStateException> {
                interactor.run(InstallMarketplaceScriptInteractor.Params(script))
            }

            coVerify(exactly = 0) { marketplaceRepository.subscribeToScript(any()) }
            coVerify(exactly = 0) { scriptInstallProvider.installScript(any(), any()) }
        }

    @Test
    fun `run should delegate cache invalidation to repository not call invalidateSubscriptionsCache itself`() =
        runTest {
            val script =
                aMarketplaceScript(
                    publicIdentifier = "com.example.script",
                    latestRelease = aRelease(),
                )
            val installedPackage = aScriptPackage("com.example.script")

            coEvery { marketplaceRepository.subscribeToScript(any()) } returns
                ScriptSubscriptionResult.Subscribed
            coEvery { scriptInstallProvider.installScript(any(), any()) } returns installedPackage

            interactor.run(InstallMarketplaceScriptInteractor.Params(script))

            // Interactor must not touch subscriptions/cache — that is the repository's responsibility.
            // Verifying exactly-1 installScript call with no other interactions on scriptInstallProvider
            // confirms no extra lifecycle calls were added to the interactor.
            coVerify(exactly = 1) { scriptInstallProvider.installScript(any(), any()) }
            confirmVerified(scriptInstallProvider)
        }

    private fun aMarketplaceScript(
        id: String = "1",
        publicIdentifier: String = "com.example.script",
        title: String = "Test Script",
        latestRelease: Release? = aRelease(),
        supportUrl: String? = null,
    ) = MarketplaceScript(
        id = id,
        publicIdentifier = publicIdentifier,
        title = title,
        latestRelease = latestRelease,
        supportUrl = supportUrl,
    )

    private fun aRelease(
        versionName: String = "1.0",
        versionCode: Long = 1L,
        releaseNotes: String? = null,
    ) = Release(
        versionName = versionName,
        versionCode = versionCode,
        releaseNotes = releaseNotes,
    )

    private fun aScriptPackage(packageName: String = "com.example.script") =
        ScriptPackage(
            source = File("/tmp/fake.jar"),
            manifest =
                Manifest(
                    packageName = packageName,
                    name = "Test Script",
                    versionCode = 1L,
                ),
            mainScript = mockk(),
            childScripts = emptyMap(),
        )
}

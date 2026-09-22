package com.cereal.client.application.interactor.brand

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.domain.repository.ScriptRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetBrandGateStatusInteractorTest {
    private val applicationConfig: ApplicationConfig = mockk(relaxed = true)
    private val authProvider: AuthProvider = mockk(relaxed = true)
    private val scriptRepository: ScriptRepository = mockk(relaxed = true)

    // Compose the real entitlement interactor so the status interactor is exercised end to end.
    private val interactor =
        GetBrandGateStatusInteractor(
            applicationConfig = applicationConfig,
            getBrandEntitlementInteractor = GetBrandEntitlementInteractor(applicationConfig, authProvider),
            scriptRepository = scriptRepository,
        )

    private fun subscriptionTo(publicIdentifier: String) =
        Subscription(
            id = "sub-$publicIdentifier",
            entitlement =
                ScriptEntitlement(
                    publicIdentifier = publicIdentifier,
                    title = publicIdentifier,
                    latestRelease = null,
                    latestDraftRelease = null,
                    shortDescription = null,
                    price = null,
                ),
        )

    private fun installedPackage(packageName: String): ScriptPackage {
        val manifest = mockk<com.cereal.client.domain.model.script.Manifest>()
        every { manifest.packageName } returns packageName
        val scriptPackage = mockk<ScriptPackage>()
        every { scriptPackage.manifest } returns manifest
        return scriptPackage
    }

    @Test
    fun `stock build is always Ready`() =
        runTest {
            every { applicationConfig.isBranded } returns false

            assertEquals(BrandGateStatus.Ready, interactor.run(Interactor.None()))
        }

    @Test
    fun `missing subscription needs subscription`() =
        runTest {
            every { applicationConfig.isBranded } returns true
            every { applicationConfig.brandScriptIds } returns listOf("com.brand.a")
            coEvery { authProvider.getSubscriptions(any()) } returns emptyList()

            assertEquals(BrandGateStatus.NeedsSubscription, interactor.run(Interactor.None()))
        }

    @Test
    fun `subscribed but not installed is unavailable`() =
        runTest {
            every { applicationConfig.isBranded } returns true
            every { applicationConfig.brandScriptIds } returns listOf("com.brand.a")
            coEvery { authProvider.getSubscriptions(any()) } returns listOf(subscriptionTo("com.brand.a"))
            coEvery { scriptRepository.getInstalledScripts() } returns flowOf(emptyList())

            assertEquals(BrandGateStatus.ScriptsUnavailable, interactor.run(Interactor.None()))
        }

    @Test
    fun `subscribed and installed is ready`() =
        runTest {
            every { applicationConfig.isBranded } returns true
            every { applicationConfig.brandScriptIds } returns listOf("com.brand.a")
            coEvery { authProvider.getSubscriptions(any()) } returns listOf(subscriptionTo("com.brand.a"))
            coEvery { scriptRepository.getInstalledScripts() } returns flowOf(listOf(installedPackage("com.brand.a")))

            assertEquals(BrandGateStatus.Ready, interactor.run(Interactor.None()))
        }
}

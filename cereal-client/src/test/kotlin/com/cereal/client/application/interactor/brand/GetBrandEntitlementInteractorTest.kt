package com.cereal.client.application.interactor.brand

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.domain.provider.AuthProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GetBrandEntitlementInteractorTest {
    private val applicationConfig: ApplicationConfig = mockk(relaxed = true)
    private val authProvider: AuthProvider = mockk(relaxed = true)
    private val interactor = GetBrandEntitlementInteractor(applicationConfig, authProvider)

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

    @Test
    fun `run returns Entitled for a stock build without hitting subscriptions`() =
        runTest {
            coEvery { applicationConfig.isBranded } returns false
            coEvery { applicationConfig.brandScriptIds } returns emptyList()

            val result = interactor.run(Interactor.None())

            assertEquals(BrandEntitlement.Entitled, result)
            coVerify(exactly = 0) { authProvider.getSubscriptions(any()) }
        }

    @Test
    fun `run returns Entitled when every brand script is subscribed`() =
        runTest {
            coEvery { applicationConfig.isBranded } returns true
            coEvery { applicationConfig.brandScriptIds } returns listOf("com.brand.a", "com.brand.b")
            coEvery { authProvider.getSubscriptions(any()) } returns
                listOf(subscriptionTo("com.brand.a"), subscriptionTo("com.brand.b"), subscriptionTo("com.other"))

            val result = interactor.run(Interactor.None())

            assertEquals(BrandEntitlement.Entitled, result)
        }

    @Test
    fun `run reports the missing scripts when a subscription is absent`() =
        runTest {
            coEvery { applicationConfig.isBranded } returns true
            coEvery { applicationConfig.brandScriptIds } returns listOf("com.brand.a", "com.brand.b")
            coEvery { authProvider.getSubscriptions(any()) } returns listOf(subscriptionTo("com.brand.a"))

            val result = interactor.run(Interactor.None())

            assertTrue(result is BrandEntitlement.MissingSubscription)
            assertEquals(listOf("com.brand.b"), (result as BrandEntitlement.MissingSubscription).missingScriptIds)
        }
}

package com.cereal.client.infrastructure.data.datasource.network

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.user.Subscription
import java.math.BigDecimal

/**
 * In-memory subscription source used in the `mock` flavor so callers that depend on
 * subscription data (UserRepository, ScriptRepository) have something to read without
 * hitting the marketplace API.
 *
 * In a `mock` + `-Pbrand` dev build it also subscribes to the Brand's scripts, so the white-label
 * entitlement gate (docs/adr/0004) resolves to "entitled" locally without a real marketplace.
 */
class FakeSubscriptionDataSource(
    private val applicationConfig: ApplicationConfig,
) : SubscriptionDataSource {
    private val baseSubscriptions =
        listOf(
            Subscription(
                id = "sub_com.cereal",
                entitlement =
                    ScriptEntitlement(
                        publicIdentifier = "com.cereal",
                        title = "Test script",
                        latestRelease = Release(versionName = "1.0.0", versionCode = 1, releaseNotes = "New: nothing new, this is the first version."),
                        latestDraftRelease = null,
                        shortDescription = "This is a test script used for testing purposes.",
                        price = BigDecimal("9.99"),
                        supportUrl = "https://github.com/Cereal-Automation/desktop-client/issues",
                    ),
            ),
            Subscription(
                id = "sub_com.cereal.monitor.tgtg",
                entitlement =
                    ScriptEntitlement(
                        publicIdentifier = "com.cereal.monitor.tgtg",
                        title = "Too Good To Go Monitor",
                        latestRelease = Release(versionName = "1.0.0", versionCode = 2, releaseNotes = "New: nothing new, this is the first version."),
                        latestDraftRelease = null,
                        shortDescription = "This is a test script used for testing purposes.",
                        price = null,
                        supportUrl = "https://github.com/Cereal-Automation/desktop-client/issues",
                    ),
            ),
        )

    // Subscribe to any Brand script not already covered above, so a mock white-label dev build is
    // entitled. Empty for stock Cereal (no brand scripts).
    private val brandSubscriptions =
        applicationConfig.brandScriptIds
            .withoutBaseDuplicates()
            .map { publicIdentifier ->
                Subscription(
                    id = "sub_$publicIdentifier",
                    entitlement =
                        ScriptEntitlement(
                            publicIdentifier = publicIdentifier,
                            title = publicIdentifier,
                            latestRelease = Release(versionName = "1.0.0", versionCode = 1, releaseNotes = "Mock brand subscription."),
                            latestDraftRelease = null,
                            shortDescription = "Mock subscription for a white-label dev build.",
                            price = null,
                            supportUrl = "https://github.com/Cereal-Automation/desktop-client/issues",
                        ),
                )
            }

    private val subscriptions = baseSubscriptions + brandSubscriptions

    override suspend fun getSubscriptions(): List<Subscription> = subscriptions

    override fun invalidateCache() {
        // No-op — the fake data is static.
    }

    private fun List<String>.withoutBaseDuplicates(): List<String> {
        val existing = baseSubscriptions.map { it.entitlement.publicIdentifier }.toSet()
        return filterNot { it in existing }
    }
}

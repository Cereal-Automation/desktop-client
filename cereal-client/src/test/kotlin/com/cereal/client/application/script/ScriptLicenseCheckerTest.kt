package com.cereal.client.application.script

import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAuthProvider
import fixtures.aScriptPackage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies the [ScriptLicenseChecker] gate over an in-memory auth provider: [ScriptLicenseChecker.isLicensed]
 * stays a faithful boolean over the same match [ScriptLicenseChecker.getEntitlement] exposes.
 */
class ScriptLicenseCheckerTest {
    private val preferences = InMemoryApplicationPreferenceRepository()

    private fun checkerWith(subscriptions: List<Subscription>): ScriptLicenseChecker = ScriptLicenseChecker(InMemoryAuthProvider(subscriptions = subscriptions), preferences)

    @Test
    fun `isLicensed is true for a subscribed script`() =
        runTest {
            val checker = checkerWith(listOf(subscription(SCRIPT_ID)))

            assertTrue(checker.isLicensed(aScriptPackage(SCRIPT_ID)))
        }

    @Test
    fun `isLicensed is false when the script is not subscribed`() =
        runTest {
            val checker = checkerWith(listOf(subscription("com.other.script")))

            assertFalse(checker.isLicensed(aScriptPackage(SCRIPT_ID)))
        }

    @Test
    fun `getEntitlement returns the matched listing`() =
        runTest {
            val checker = checkerWith(listOf(subscription(SCRIPT_ID)))

            val entitlement = checker.getEntitlement(aScriptPackage(SCRIPT_ID))

            assertEquals(SCRIPT_ID, entitlement?.publicIdentifier)
        }

    @Test
    fun `getEntitlement returns null when the script is not entitled`() =
        runTest {
            val checker = checkerWith(emptyList())

            assertNull(checker.getEntitlement(aScriptPackage(SCRIPT_ID)))
        }

    private companion object {
        private const val SCRIPT_ID = "com.example.script"

        private fun subscription(publicId: String) =
            Subscription(
                id = "sub-$publicId",
                entitlement =
                    ScriptEntitlement(
                        publicIdentifier = publicId,
                        title = publicId,
                        latestRelease = null,
                        latestDraftRelease = null,
                        shortDescription = null,
                        price = null,
                        capacity = ScriptCapacity.None,
                    ),
            )
    }
}

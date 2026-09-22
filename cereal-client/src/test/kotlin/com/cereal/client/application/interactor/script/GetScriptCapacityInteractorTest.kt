package com.cereal.client.application.interactor.script

import com.cereal.client.application.script.ScriptLicenseChecker
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAuthProvider
import fixtures.aScriptPackage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Behavioural tests for [GetScriptCapacityInteractor] wired to a real [ScriptLicenseChecker] over an
 * in-memory [InMemoryAuthProvider] (seeded subscriptions / team scripts) and an in-memory preference
 * repository. Asserts the entitled [ScriptCapacity] surfaced for the start-time seam, gated on the
 * script actually being entitled.
 */
class GetScriptCapacityInteractorTest {
    private val preferences = InMemoryApplicationPreferenceRepository()

    private fun interactorWith(
        subscriptions: List<Subscription> = emptyList(),
        teamScripts: List<ScriptEntitlement> = emptyList(),
    ): GetScriptCapacityInteractor {
        val authProvider = InMemoryAuthProvider(subscriptions = subscriptions, teamScripts = teamScripts)
        return GetScriptCapacityInteractor(ScriptLicenseChecker(authProvider, preferences))
    }

    @Test
    fun `run returns the finite capacity of a subscribed script`() =
        runTest {
            val interactor =
                interactorWith(subscriptions = listOf(subscription(SCRIPT_ID, ScriptCapacity.Limited(100, "records"))))

            val result = interactor.run(GetScriptCapacityInteractor.Params(aScriptPackage(SCRIPT_ID)))

            assertEquals(ScriptCapacity.Limited(100, "records"), result)
        }

    @Test
    fun `run returns the unlimited capacity of a subscribed script`() =
        runTest {
            val interactor =
                interactorWith(subscriptions = listOf(subscription(SCRIPT_ID, ScriptCapacity.Unlimited("records"))))

            val result = interactor.run(GetScriptCapacityInteractor.Params(aScriptPackage(SCRIPT_ID)))

            assertEquals(ScriptCapacity.Unlimited("records"), result)
        }

    @Test
    fun `run returns None when the subscribed script has no capacity concept`() =
        runTest {
            val interactor = interactorWith(subscriptions = listOf(subscription(SCRIPT_ID, ScriptCapacity.None)))

            val result = interactor.run(GetScriptCapacityInteractor.Params(aScriptPackage(SCRIPT_ID)))

            assertEquals(ScriptCapacity.None, result)
        }

    @Test
    fun `run returns None when the script is not entitled`() =
        runTest {
            // A subscription exists, but to a different script than the one being started.
            val interactor =
                interactorWith(subscriptions = listOf(subscription("com.other.script", ScriptCapacity.Limited(5, "records"))))

            val result = interactor.run(GetScriptCapacityInteractor.Params(aScriptPackage(SCRIPT_ID)))

            assertEquals(ScriptCapacity.None, result)
        }

    @Test
    fun `run returns a team script capacity when development scripts are enabled`() =
        runTest {
            preferences.setDevelopmentScriptsEnabled(true)
            val interactor =
                interactorWith(teamScripts = listOf(listing(SCRIPT_ID, ScriptCapacity.Limited(750, "records"))))

            val result = interactor.run(GetScriptCapacityInteractor.Params(aScriptPackage(SCRIPT_ID)))

            assertEquals(ScriptCapacity.Limited(750, "records"), result)
        }

    @Test
    fun `run ignores team scripts when development scripts are disabled`() =
        runTest {
            // Dev scripts default off: the team-script entitlement must not be consulted, so no match.
            val interactor =
                interactorWith(teamScripts = listOf(listing(SCRIPT_ID, ScriptCapacity.Limited(750, "records"))))

            val result = interactor.run(GetScriptCapacityInteractor.Params(aScriptPackage(SCRIPT_ID)))

            assertEquals(ScriptCapacity.None, result)
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

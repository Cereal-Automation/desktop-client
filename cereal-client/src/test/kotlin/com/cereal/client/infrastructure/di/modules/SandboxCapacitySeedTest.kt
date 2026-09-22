package com.cereal.client.infrastructure.di.modules

import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import com.cereal.client.infrastructure.provider.SandboxAuthProvider
import fixtures.aScriptPackage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Guards the `mock`-flavor capacity demo seed: the sandbox subscription entitlements must carry
 * capacity so the running mock app exercises the start/config capacity display. This asserts the seed
 * value reaches the data path that surface reads from — the derived subscription entitlements
 * ([SandboxAuthProvider.getSubscriptions]). The display code that turns a [ScriptCapacity] into a row
 * is covered separately by `ScriptSelectionCapacityScreenTest`.
 *
 * (The marketplace catalog no longer carries capacity — the backend does not serve it there, so the
 * detail dialog shows no capacity row.)
 *
 * If someone drops the seed, the mock demo silently loses its capacity row — this fails first.
 */
class SandboxCapacitySeedTest {
    @Test
    fun `sandbox subscriptions attach the finite cap to the installed sample script`() =
        runTest {
            val scriptRepository =
                InMemoryScriptRepository().apply {
                    seed(listOf(aScriptPackage("com.cereal"), aScriptPackage("com.other.installed")))
                }
            val authProvider = SandboxAuthProvider(scriptRepository)

            val capacities =
                authProvider
                    .getSubscriptions()
                    .associate { it.entitlement.publicIdentifier to it.entitlement.capacity }

            assertEquals(ScriptCapacity.Limited(50, "records"), capacities.getValue("com.cereal"))
            // Any other installed script has no seeded cap and derives None (shows nothing, never blocks).
            assertEquals(ScriptCapacity.None, capacities.getValue("com.other.installed"))
        }
}

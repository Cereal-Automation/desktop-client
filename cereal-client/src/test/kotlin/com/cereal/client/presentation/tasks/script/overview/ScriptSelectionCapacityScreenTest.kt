package com.cereal.client.presentation.tasks.script.overview

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.cereal.client.domain.model.script.ScriptCapacity
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import com.cereal.client.infrastructure.di.Injector
import com.cereal.client.infrastructure.di.modules.InMemoryProviderModule
import com.cereal.client.infrastructure.di.modules.InMemoryRepositoryModule
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAuthProvider
import com.cereal.client.presentation.theme.CerealTheme
import fixtures.aScriptPackage
import org.jetbrains.skiko.Library
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test

/**
 * Renders the real [ScriptSelectionDialog] through the full ViewModel/interactor graph on in-memory
 * repositories, seeding an installed script plus a subscription entitlement so the entitled record
 * capacity (ticket #740) is observable in the config pane. Drives the actual Compose surface — the
 * capacity row is fetched via `GetScriptCapacityInteractor` and rendered through the shared
 * `scriptCapacityText` formatter, exactly as it does in the running app.
 *
 * Self-skips on headless environments lacking skiko's native graphics stack (see [runCapacityScreenTest]).
 */
@OptIn(ExperimentalTestApi::class)
class ScriptSelectionCapacityScreenTest {
    @Test
    fun `shows the entitled finite record capacity for the selected script`() =
        runCapacityScreenTest(ScriptCapacity.Limited(100, "records")) {
            waitForText("100 records")
            onNodeWithText("100 records").assertIsDisplayed()
            onNodeWithText("Capacity").assertIsDisplayed()
        }

    @Test
    fun `shows an unlimited record capacity for the selected script`() =
        runCapacityScreenTest(ScriptCapacity.Unlimited("records")) {
            waitForText("Unlimited records")
            onNodeWithText("Unlimited records").assertIsDisplayed()
            onNodeWithText("Capacity").assertIsDisplayed()
        }

    @Test
    fun `shows no capacity row when the selected script has no capacity concept`() =
        runCapacityScreenTest(ScriptCapacity.None) {
            // The script is entitled but uncapped, so no capacity label or amount is rendered.
            waitForText("Test")
            onNodeWithText("Capacity").assertDoesNotExist()
        }

    /**
     * Boots a fresh Koin graph (in-memory modules) with the [AuthProvider] and [ScriptRepository]
     * overridden so [PACKAGE_NAME] is installed and entitled with [capacity], renders the dialog
     * auto-selecting that script, and runs [assertions] against the live surface.
     */
    private fun runCapacityScreenTest(
        capacity: ScriptCapacity,
        assertions: ComposeUiTest.() -> Unit,
    ) {
        assumeTrue(composeRenderingAvailable, "Compose desktop rendering is unavailable in this environment")

        val authProvider = InMemoryAuthProvider(subscriptions = listOf(subscription(PACKAGE_NAME, capacity)))
        val scriptRepository = InMemoryScriptRepository().apply { seed(listOf(aScriptPackage(PACKAGE_NAME))) }
        val overrides =
            module {
                single<AuthProvider> { authProvider }
                single<ScriptRepository> { scriptRepository }
            }

        runDesktopComposeUiTest {
            stopKoin()
            startKoin {
                modules(Injector.appModules(InMemoryRepositoryModule.modules, InMemoryProviderModule.modules) + overrides)
            }
            try {
                setContent {
                    CerealTheme {
                        ScriptSelectionDialog(
                            scriptPackageGroup = ScriptPackageGroup("group-1", "Group 1"),
                            initialPublicIdentifier = PACKAGE_NAME,
                            onScriptInstanceCreated = {},
                            onDismissRequest = {},
                            onNavigateToMarketplace = {},
                        )
                    }
                }
                assertions()
            } finally {
                stopKoin()
            }
        }
    }

    /** Polls until [text] appears — the capacity fetch resolves on a background dispatcher. */
    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

    private companion object {
        private const val PACKAGE_NAME = "com.demo.capacity"

        private val composeRenderingAvailable: Boolean by lazy {
            runCatching { Library.load() }.isSuccess
        }

        private fun subscription(
            publicId: String,
            capacity: ScriptCapacity,
        ) = Subscription(
            id = "sub-$publicId",
            entitlement =
                ScriptEntitlement(
                    publicIdentifier = publicId,
                    title = publicId,
                    latestRelease = null,
                    latestDraftRelease = null,
                    shortDescription = null,
                    price = null,
                    capacity = capacity,
                ),
        )
    }
}

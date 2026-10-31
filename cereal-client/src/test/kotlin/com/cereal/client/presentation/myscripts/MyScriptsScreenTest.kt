package com.cereal.client.presentation.myscripts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cereal.client.application.agent.AI_BROWSER_AGENT_PACKAGE
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.marketplace.ScriptOwner
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.provider.MarketplaceProvider
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryMarketplaceProvider
import fixtures.aScriptPackage
import fixtures.aScriptPackageWithVariant
import fixtures.aTask
import fixtures.anAgentPackage
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders [MyScriptsScreen] on in-memory repositories. The shared [InMemoryScriptRepository] starts
 * with no installed scripts, so the empty state is the default rendered state. Tests that need
 * installed scripts seed them, and swap the marketplace provider to control which ones have a listing.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class MyScriptsScreenTest {
    @Test
    fun rendersEmptyState() =
        runScreenTest {
            setScreenContent { MyScriptsScreen(onOpenMarketplace = {}) }

            onNodeWithText("My Scripts").assertIsDisplayed()
            onNodeWithText("No scripts installed yet").assertIsDisplayed()
            onNodeWithText("Browse marketplace").assertIsDisplayed()
        }

    @Test
    fun browseMarketplaceClickFiresCallback() =
        runScreenTest {
            var marketplaceOpened = false
            setScreenContent { MyScriptsScreen(onOpenMarketplace = { marketplaceOpened = true }) }

            onNodeWithText("Browse marketplace").performClick()

            assertTrue(marketplaceOpened, "onOpenMarketplace callback should fire when Browse marketplace is clicked")
        }

    @Test
    fun listsTheAiBrowserAgentWithoutVariantsAsItsOwnEntry() =
        runScreenTest {
            seedInstalled(anAgentPackage(), aScriptPackage("com.example.other"))
            setScreenContent { MyScriptsScreen(onOpenMarketplace = {}) }

            waitForText("Uninstall")
            onNodeWithText("Test").assertIsDisplayed()
            onAllNodesWithText("AI Browser Agent").assertCountEquals(1)
        }

    @Test
    fun listsVariantsOfTheAiBrowserAgentWithoutUninstall() =
        runScreenTest(overrides = listOf(marketplaceWith())) {
            val agent =
                anAgentPackage().copy(
                    variants = aScriptPackageWithVariant(AI_BROWSER_AGENT_PACKAGE, "product-checkout", "Product Monitor & Checkout").variants,
                )
            seedInstalled(agent, aScriptPackage("com.example.other"))
            setScreenContent { MyScriptsScreen(onOpenMarketplace = {}) }

            waitForText("Uninstall")
            onNodeWithText("Product Monitor & Checkout").assertIsDisplayed()
            onAllNodesWithText("AI Browser Agent").assertCountEquals(0)
            // Only the other script offers Uninstall: removing the agent's jar would remove Home's goal composer too.
            onAllNodesWithText("Uninstall").assertCountEquals(1)
        }

    @Test
    fun unmatchedRowShowsInlineUninstall() =
        runScreenTest(overrides = listOf(marketplaceWith())) {
            seedInstalled(aScriptPackage("com.example.team"))
            setScreenContent { MyScriptsScreen(onOpenMarketplace = {}) }

            waitForText("Uninstall")
            onNodeWithText("Uninstall").assertIsDisplayed()
        }

    @Test
    fun inlineUninstallIsDisabledWhileTheScriptHasRunningTasks() =
        runScreenTest(overrides = listOf(marketplaceWith())) {
            seedInstalled(aScriptPackage("com.example.team"))
            runBlocking {
                KoinJavaComponent
                    .get<TasksRepository>(TasksRepository::class.java)
                    .addTask(aTask("task-1", "com.example.team", running = true))
            }
            setScreenContent { MyScriptsScreen(onOpenMarketplace = {}) }

            waitForText("Running tasks")
            onNodeWithText("Uninstall").assertIsNotEnabled()
        }

    @Test
    fun clickingAMatchedRowOpensTheScriptDetailDialog() =
        runScreenTest(overrides = listOf(marketplaceWith(listedScript))) {
            seedInstalled(aScriptPackage(listedScript.publicIdentifier))
            setScreenContent { MyScriptsScreen(onOpenMarketplace = {}) }

            waitForText("Listed Script")
            onNodeWithText("@acme").assertIsDisplayed()
            onAllNodesWithText("Uninstall").assertCountEquals(0)

            onNodeWithText("Listed Script").performClick()

            waitForText("The full description.")
        }

    private val listedScript =
        MarketplaceScript(
            id = "1",
            publicIdentifier = "com.example.listed",
            title = "Listed Script",
            description = "The full description.",
            shortDescription = "Short blurb.",
            isFree = true,
            developer = ScriptOwner(name = "acme"),
        )

    private fun marketplaceWith(vararg listings: MarketplaceScript) = module { single<MarketplaceProvider> { InMemoryMarketplaceProvider(listings.toList()) } }

    private fun seedInstalled(vararg packages: ScriptPackage) =
        (KoinJavaComponent.get<ScriptRepository>(ScriptRepository::class.java) as InMemoryScriptRepository)
            .seed(packages.toList())

    private fun ComposeUiTest.waitForText(text: String) = waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
}

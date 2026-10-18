package com.cereal.client.presentation.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cereal.client.application.exception.CerealException
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.provider.MarketplaceProvider
import com.cereal.client.domain.provider.ScriptInstallProvider
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryMarketplaceProvider
import fixtures.FakeScriptInstallProvider
import fixtures.anAgentListing
import fixtures.anAgentPackage
import fixtures.anAgentRequiringNewerClient
import org.koin.dsl.module
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders [HomeScreen] with the AI browser agent installed on the in-memory graph. Self-skips where skiko is unavailable. */
@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {
    private val agentInstalled =
        module { single<ScriptRepository> { InMemoryScriptRepository().apply { seed(listOf(anAgentPackage())) } } }

    @Test
    fun `renders the pitch and the goal composer`() =
        runScreenTest(overrides = listOf(agentInstalled)) {
            setScreenContent { HomeScreen() }

            onNodeWithText("A free AI browser agent that runs on your machine, with your model and your key.").assertIsDisplayed()
            onNodeWithText("What should the agent do?").assertIsDisplayed()
            onNodeWithText("Start").assertIsDisplayed()
            onNodeWithText("More options").assertIsDisplayed()
            onNodeWithText("Runs in your own Chrome and asks before it buys or sends anything.").assertIsDisplayed()
        }

    @Test
    fun `check every appears only in Repeat`() =
        runScreenTest(overrides = listOf(agentInstalled)) {
            setScreenContent { HomeScreen() }

            onNodeWithText("Check every").assertDoesNotExist()
            onNodeWithText("Repeat").performClick()
            onNodeWithText("Check every").assertIsDisplayed()
            onNodeWithText("Once").performClick()
            onNodeWithText("Check every").assertDoesNotExist()
        }

    @Test
    fun `an example chip fills the goal`() =
        runScreenTest(overrides = listOf(agentInstalled)) {
            setScreenContent { HomeScreen() }

            onNodeWithText("Watch a product for restock").performClick()

            onNodeWithTag(HomeTestTags.GOAL).assertTextContains("Tell me when this product is back in stock")
        }
}

/** The cards that replace the composer when the agent can't run, on the in-memory graph. */
@OptIn(ExperimentalTestApi::class)
class HomeSlotCardsScreenTest {
    private val scripts = InMemoryScriptRepository()
    private val installProvider = FakeScriptInstallProvider(scripts)

    private fun world(
        installed: ScriptPackage? = null,
        listing: MarketplaceScript = anAgentListing(),
    ) = module {
        scripts.seed(listOfNotNull(installed))
        single<ScriptRepository> { scripts }
        single<ScriptInstallProvider> { installProvider }
        single<MarketplaceProvider> { InMemoryMarketplaceProvider(catalog = listOf(listing)) }
    }

    @Test
    fun `install card installs the agent and gives way to the composer`() =
        runScreenTest(overrides = listOf(world())) {
            setScreenContent { HomeScreen() }

            onNodeWithText("The AI browser agent isn't installed").assertIsDisplayed()
            onNodeWithText("What should the agent do?").assertDoesNotExist()
            onNodeWithText("Install").performClick()

            waitUntil { onAllNodesWithText("What should the agent do?").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("The AI browser agent isn't installed").assertDoesNotExist()
        }

    @Test
    fun `a failed install shows the error and Retry`() =
        runScreenTest(overrides = listOf(world())) {
            installProvider.failWith = CerealException("Download failed")
            setScreenContent { HomeScreen() }

            onNodeWithText("Install").performClick()

            waitUntil { onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Download failed").assertIsDisplayed()
        }

    @Test
    fun `update card opens the client update flow`() =
        runScreenTest(overrides = listOf(world(installed = anAgentRequiringNewerClient()))) {
            var opened = false
            setScreenContent { HomeScreen(onOpenClientUpdate = { opened = true }) }

            onNodeWithText("Update Cereal to use the AI browser agent").assertIsDisplayed()
            onNodeWithText("What should the agent do?").assertDoesNotExist()
            onNodeWithText("Update Cereal").performClick()

            assertTrue(opened)
        }

    @Test
    fun `maintenance mode shows the banner over a working composer`() =
        runScreenTest(overrides = listOf(world(installed = anAgentPackage(), listing = anAgentListing(maintenanceMode = true)))) {
            setScreenContent { HomeScreen() }

            onNodeWithText("The AI browser agent is under maintenance. Runs may not work right now.", substring = true).assertIsDisplayed()
            onNodeWithText("Start").assertIsEnabled()
        }
}

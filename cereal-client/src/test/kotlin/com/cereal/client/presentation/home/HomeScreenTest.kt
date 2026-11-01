package com.cereal.client.presentation.home

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.cereal.client.application.exception.CerealException
import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.provider.LlmProvider
import com.cereal.client.domain.provider.MarketplaceProvider
import com.cereal.client.domain.provider.ScriptInstallProvider
import com.cereal.client.domain.repository.LlmProviderConfigRepository
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLlmProviderConfigRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryLlmProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryMarketplaceProvider
import com.cereal.client.presentation.settings.ai.AI_API_KEY_TAG
import fixtures.FakeScriptInstallProvider
import fixtures.anAgentListing
import fixtures.anAgentPackage
import fixtures.anAgentRequiringNewerClient
import fixtures.anAgentWithCheckoutVariant
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders [HomeScreen] with the AI browser agent installed on the in-memory graph. Self-skips where skiko is unavailable. */
@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {
    private val agentInstalled =
        module { single<ScriptRepository> { InMemoryScriptRepository().apply { seed(listOf(anAgentPackage())) } } }
    private val providerConfigured =
        module {
            single<LlmProviderConfigRepository> {
                InMemoryLlmProviderConfigRepository(LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "key", "claude-opus-5-5"))
            }
        }
    private val ready = listOf(agentInstalled, providerConfigured)

    private fun savedConfig(): LlmProviderConfig? = runBlocking { KoinJavaComponent.get<LlmProviderConfigRepository>(LlmProviderConfigRepository::class.java).get() }

    private fun ComposeUiTest.awaitText(text: String) = waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.enterApiKey(key: String) = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(AI_API_KEY_TAG))).performTextInput(key)

    @Test
    fun `renders the goal composer with the connected model in place of the pitch`() =
        runScreenTest(overrides = ready) {
            setScreenContent { HomeScreen() }

            onNodeWithText("A free AI browser agent that runs on your machine, with your model and your key.").assertDoesNotExist()
            onNodeWithText("Tell the agent what to do").assertIsDisplayed()
            onNodeWithText("Start").assertIsDisplayed()
            onNodeWithText("More options").assertIsDisplayed()
            onNodeWithText("Ready when you are").assertIsDisplayed()
            onNodeWithText("Change").assertIsDisplayed()
            onNodeWithText("claude-opus-5-5").assertIsDisplayed()
            onNodeWithText("Connect an AI model").assertDoesNotExist()
        }

    @Test
    fun `renders the script's required fields, with Check every and Vary by only in Repeat`() =
        runScreenTest(overrides = ready) {
            setScreenContent { HomeScreen() }
            awaitText("goal")

            onNodeWithText("startUrl").assertIsDisplayed()
            onNodeWithText("maxSteps").assertDoesNotExist()
            onNodeWithText("repeatInterval").assertDoesNotExist()
            onNodeWithText("Once").assertIsSelected()
            onNodeWithText("Repeat").performClick()
            onNodeWithText("Repeat").assertIsSelected()
            onNodeWithText("repeatInterval").assertIsDisplayed()
            onNodeWithText("repeatJitter").assertIsDisplayed()
            onNodeWithText("Once").performClick()
            onNodeWithText("repeatInterval").assertDoesNotExist()
        }

    @Test
    fun `picking a variant in the workflow picker swaps in that variant's fields`() =
        runScreenTest(
            overrides =
                listOf(
                    module { single<ScriptRepository> { InMemoryScriptRepository().apply { seed(listOf(anAgentWithCheckoutVariant())) } } },
                    providerConfigured,
                ),
        ) {
            setScreenContent { HomeScreen() }
            awaitText("goal")
            onNodeWithText("2 available").assertIsDisplayed()

            onNodeWithContentDescription("Workflow").performClick()
            onNode(hasText("Product Checkout") and hasClickAction()).performClick()

            awaitText("productUrl")
            onNodeWithText("goal").assertDoesNotExist()
        }

    @Test
    fun `an agent without variants shows no workflow picker`() =
        runScreenTest(overrides = ready) {
            setScreenContent { HomeScreen() }
            awaitText("goal")

            onNodeWithContentDescription("Workflow").assertDoesNotExist()
        }

    @Test
    fun `with no AI provider the setup card replaces the composer with Gemini pre-selected`() =
        runScreenTest(overrides = listOf(agentInstalled)) {
            setScreenContent { HomeScreen() }

            awaitText("Connect an AI model")
            onNodeWithText("Google Gemini").assertIsSelected()
        }

    @Test
    fun `the goal step stays locked until a model is connected`() =
        runScreenTest(overrides = listOf(agentInstalled)) {
            setScreenContent { HomeScreen() }
            awaitText("Connect an AI model")

            onNodeWithText("Available once a model is connected.").performScrollTo().assertIsDisplayed()
            onNodeWithText("A free AI browser agent that runs on your machine, with your model and your key.").assertIsDisplayed()
            onNodeWithTag(HomeTestTags.FIELDS).assertDoesNotExist()
            onNodeWithText("Start").assertDoesNotExist()
        }

    @Test
    fun `a failing Connect shows the error and saves nothing`() =
        runScreenTest(overrides = listOf(agentInstalled)) {
            (KoinJavaComponent.get<LlmProvider>(LlmProvider::class.java) as InMemoryLlmProvider).listModelsError = LlmCallException.Authentication()
            setScreenContent { HomeScreen() }
            awaitText("Connect an AI model")

            enterApiKey("bad-key")
            onNodeWithText("Connect").performClick()

            awaitText("Bad API key: the provider rejected it.")
            onNodeWithText("Connect an AI model").assertIsDisplayed()
            assertNull(savedConfig())
        }

    @Test
    fun `a passing Connect saves the config and swaps the card for the composer`() =
        runScreenTest(overrides = listOf(agentInstalled)) {
            setScreenContent { HomeScreen() }
            awaitText("Connect an AI model")

            enterApiKey("good-key")
            onNodeWithText("Connect").performClick()

            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Connect an AI model").fetchSemanticsNodes().isEmpty() }
            onNodeWithTag(HomeTestTags.FIELDS).assertExists()
            onNodeWithText("gemini-3.8-flash").assertIsDisplayed()
            onNodeWithText("Change").assertIsDisplayed()
            // Connecting in place keeps the guided layout instead of jumping to the composer-first one.
            onNodeWithText("Model connected").assertIsDisplayed()
            onNodeWithText("A free AI browser agent that runs on your machine, with your model and your key.").assertIsDisplayed()
            assertEquals("gemini-3.8-flash", savedConfig()?.model)
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
        single<LlmProviderConfigRepository> {
            InMemoryLlmProviderConfigRepository(LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "key", "claude-opus-5-5"))
        }
    }

    @Test
    fun `install card installs the agent and gives way to the composer`() =
        runScreenTest(overrides = listOf(world())) {
            setScreenContent { HomeScreen() }

            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("The AI browser agent isn't installed").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("The AI browser agent isn't installed").assertIsDisplayed()
            onNodeWithText("Tell the agent what to do").assertDoesNotExist()
            onNodeWithText("Install").performClick()

            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Tell the agent what to do").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("The AI browser agent isn't installed").assertDoesNotExist()
        }

    @Test
    fun `a failed install shows the error and Retry`() =
        runScreenTest(overrides = listOf(world())) {
            installProvider.failWith = CerealException("Download failed")
            setScreenContent { HomeScreen() }

            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Install").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Install").performClick()

            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Download failed").assertIsDisplayed()
        }

    @Test
    fun `update card opens the client update flow`() =
        runScreenTest(overrides = listOf(world(installed = anAgentRequiringNewerClient()))) {
            var opened = false
            setScreenContent { HomeScreen(onOpenClientUpdate = { opened = true }) }

            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Update Cereal to use the AI browser agent").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Update Cereal to use the AI browser agent").assertIsDisplayed()
            onNodeWithText("Tell the agent what to do").assertDoesNotExist()
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

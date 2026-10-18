package com.cereal.client.presentation.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import fixtures.anAgentPackage
import org.koin.dsl.module
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test

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

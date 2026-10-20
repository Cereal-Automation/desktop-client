package com.cereal.client.presentation.myscripts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import fixtures.aScriptPackage
import fixtures.anAgentPackage
import org.koin.java.KoinJavaComponent
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders [MyScriptsScreen] on in-memory repositories. The shared [InMemoryScriptRepository] starts
 * with no installed scripts, so the empty state is the default rendered state. Covers the empty-state
 * render and the "Browse marketplace" callback firing.
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
    fun hidesUninstallForTheAiBrowserAgentOnly() =
        runScreenTest {
            (KoinJavaComponent.get<ScriptRepository>(ScriptRepository::class.java) as InMemoryScriptRepository)
                .seed(listOf(anAgentPackage(), aScriptPackage("com.example.other")))
            setScreenContent { MyScriptsScreen(onOpenMarketplace = {}) }

            onNodeWithText("AI Browser Agent").assertIsDisplayed()
            onAllNodesWithText("Uninstall").assertCountEquals(1)
        }
}

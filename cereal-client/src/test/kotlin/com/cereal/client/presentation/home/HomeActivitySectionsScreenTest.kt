package com.cereal.client.presentation.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import com.cereal.client.application.agent.AI_BROWSER_AGENT_PACKAGE
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import com.cereal.client.presentation.tasks.script.overview.ScriptRequest
import fixtures.aGoalRun
import fixtures.aScriptPackage
import fixtures.aScriptPackageWithVariant
import fixtures.anAgentPackage
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals

/** Home's recent goal runs and ready-made scripts strip on the in-memory graph. Self-skips where skiko is unavailable. */
@OptIn(ExperimentalTestApi::class)
class HomeActivitySectionsScreenTest {
    private val restock = aScriptPackage("com.example.restock").let { it.copy(manifest = it.manifest.copy(name = "Restock Monitor")) }

    private val withRunAndScript =
        module {
            single<ScriptRepository> { InMemoryScriptRepository().apply { seed(listOf(anAgentPackage(), restock)) } }
            single<TasksRepository> {
                InMemoryTasksRepository().apply {
                    runBlocking {
                        addTask(
                            aGoalRun(
                                "t1",
                                goal = "Tell me when the pliers are back in stock",
                                startUrl = "https://shop.example/pliers",
                                statusMessages = listOf("Checked. Cost: $0.02"),
                            ),
                        )
                    }
                }
            }
        }

    private val agentOnly =
        module { single<ScriptRepository> { InMemoryScriptRepository().apply { seed(listOf(anAgentPackage())) } } }

    @Test
    fun `recent runs open Tasks focused on the run, and All in Tasks opens Tasks`() {
        val opened = mutableListOf<String?>()
        runScreenTest(overrides = listOf(withRunAndScript)) {
            setScreenContent { HomeScreen(onOpenTasks = { opened += it }) }

            // Task updates are debounced on a real dispatcher.
            waitUntilAtLeastOneExists(hasText("Running · Once · shop.example · $0.02"), LOAD_TIMEOUT_MS)
            onNodeWithText("Running · Once · shop.example · $0.02").performScrollTo().assertIsDisplayed()
            onNodeWithText("Tell me when the pliers are back in stock").performScrollTo().performClick()
            onNodeWithText("All in Tasks →").performScrollTo().performClick()
        }
        assertEquals(listOf("t1", null), opened)
    }

    @Test
    fun `the strip runs an installed ready-made script and browses the marketplace`() {
        val run = mutableListOf<ScriptRequest>()
        var marketplace = 0
        runScreenTest(overrides = listOf(withRunAndScript)) {
            setScreenContent { HomeScreen(onNavigateToMarketplace = { marketplace++ }, onRunScript = { run += it }) }

            waitUntilAtLeastOneExists(hasText("Restock Monitor"), LOAD_TIMEOUT_MS)
            onNodeWithText("Restock Monitor").performScrollTo().assertIsDisplayed()
            onNodeWithText("AI Browser Agent").assertDoesNotExist()
            onNodeWithText("Run").performScrollTo().performClick()
            onNodeWithText("Browse marketplace →").performScrollTo().performClick()
        }
        assertEquals(listOf(ScriptRequest("com.example.restock")), run)
        assertEquals(1, marketplace)
    }

    @Test
    fun `running an agent variant from the strip requests that variant`() {
        val run = mutableListOf<ScriptRequest>()
        val agent =
            anAgentPackage().copy(
                variants = aScriptPackageWithVariant(AI_BROWSER_AGENT_PACKAGE, "product-checkout", "Product Monitor & Checkout").variants,
            )
        val withVariant = module { single<ScriptRepository> { InMemoryScriptRepository().apply { seed(listOf(agent)) } } }
        runScreenTest(overrides = listOf(withVariant)) {
            setScreenContent { HomeScreen(onRunScript = { run += it }) }

            waitUntilAtLeastOneExists(hasText("Product Monitor & Checkout"), LOAD_TIMEOUT_MS)
            onNodeWithText("Run").performScrollTo().performClick()
        }
        assertEquals(listOf(ScriptRequest(AI_BROWSER_AGENT_PACKAGE, "product-checkout")), run)
    }

    @Test
    fun `empty states when there are no goal runs and no ready-made scripts`() =
        runScreenTest(overrides = listOf(agentOnly)) {
            setScreenContent { HomeScreen() }

            onNodeWithText("No goal runs yet. Describe a goal above to start one.").performScrollTo().assertIsDisplayed()
            onNodeWithText("No ready-made scripts installed yet.").performScrollTo().assertIsDisplayed()
        }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}

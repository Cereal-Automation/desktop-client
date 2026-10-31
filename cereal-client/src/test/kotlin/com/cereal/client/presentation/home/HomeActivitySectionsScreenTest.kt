package com.cereal.client.presentation.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import fixtures.aGoalRun
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals

/** Home's recent goal runs on the in-memory graph. Self-skips where skiko is unavailable. */
@OptIn(ExperimentalTestApi::class)
class HomeActivitySectionsScreenTest {
    private val withRun =
        module {
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

    @Test
    fun `recent runs open Tasks focused on the run, and All in Tasks opens Tasks`() {
        val opened = mutableListOf<String?>()
        runScreenTest(overrides = listOf(withRun)) {
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
    fun `empty state when there are no goal runs`() =
        runScreenTest {
            setScreenContent { HomeScreen() }

            onNodeWithText("No goal runs yet. Describe a goal above to start one.").performScrollTo().assertIsDisplayed()
        }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}

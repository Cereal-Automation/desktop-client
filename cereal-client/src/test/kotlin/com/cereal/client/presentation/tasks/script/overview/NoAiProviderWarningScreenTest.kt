package com.cereal.client.presentation.tasks.script.overview

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cereal.client.domain.model.script.ScriptFeature
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import fixtures.aScriptPackage
import org.koin.dsl.module
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test

/**
 * Starting an `llm` script with no AI provider saved (the in-memory graph starts unconfigured) shows the
 * AI warning dialog. Self-skips where skiko is unavailable.
 */
@OptIn(ExperimentalTestApi::class)
class NoAiProviderWarningScreenTest {
    @Test
    fun `starting an llm script without an AI provider shows the AI warning`() {
        val script = aScriptPackage(PACKAGE).let { it.copy(manifest = it.manifest.copy(features = setOf(ScriptFeature.LLM))) }
        val scriptRepository = InMemoryScriptRepository().apply { seed(listOf(script)) }
        val overrides = module { single<ScriptRepository> { scriptRepository } }

        runScreenTest(overrides = listOf(overrides)) {
            setScreenContent {
                ScriptSelectionDialog(
                    scriptPackageGroup = ScriptPackageGroup("group-1", "Group 1"),
                    initialPublicIdentifier = PACKAGE,
                    onScriptInstanceCreated = {},
                    onDismissRequest = {},
                    onNavigateToMarketplace = {},
                )
            }
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("Test").fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithContentDescription("Start task").performClick()

            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("AI provider not set up").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Start anyway").assertExists()
        }
    }

    private companion object {
        private const val PACKAGE = "com.demo.ai"
    }
}

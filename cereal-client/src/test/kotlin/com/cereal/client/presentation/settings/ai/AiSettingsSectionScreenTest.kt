package com.cereal.client.presentation.settings.ai

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.client.domain.repository.LlmProviderConfigRepository
import com.cereal.client.presentation.settings.ApplicationSettingsScreen
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Settings → AI on the in-memory repository graph: what the form shows for each provider choice and
 * what Save persists. Self-skips on headless CI via the screen harness.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class AiSettingsSectionScreenTest {
    private val repository: LlmProviderConfigRepository
        get() = KoinJavaComponent.get(LlmProviderConfigRepository::class.java)

    private fun savedConfig(): LlmProviderConfig? = runBlocking { repository.get() }

    private fun ComposeUiTest.input(tag: String): SemanticsNodeInteraction = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    private fun ComposeUiTest.awaitText(text: String) = waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.awaitSaved(predicate: (LlmProviderConfig?) -> Boolean) = waitUntil(timeoutMillis = 5_000) { predicate(savedConfig()) }

    @Test
    fun settingsScreenShowsAiSectionAfterNotifications() =
        runScreenTest {
            setScreenContent { ApplicationSettingsScreen() }

            onNode(hasScrollAction()).performScrollToNode(hasText("Choose the LLM provider scripts use."))
            onNodeWithText("AI").assertIsDisplayed()
        }

    @Test
    fun noneHidesTheProviderFields() =
        runScreenTest {
            setScreenContent { AiSettingsSection() }

            onNodeWithText("None").assertIsDisplayed()
            onNodeWithText("Base URL").assertDoesNotExist()
        }

    @Test
    fun anthropicRevealsFieldsWithItsDefaults() =
        runScreenTest {
            setScreenContent { AiSettingsSection() }

            onNodeWithText("Anthropic").performClick()

            onNodeWithText("Base URL").assertIsDisplayed()
            input(AI_BASE_URL_TAG).assertTextEquals("https://api.anthropic.com/v1")
            input(AI_MODEL_TAG).assertTextEquals("claude-opus-5-5")
            onNodeWithText("console.anthropic.com", substring = true).assertIsDisplayed()

            onNodeWithText("None").performClick()
            onNodeWithText("Base URL").assertDoesNotExist()
        }

    @Test
    fun openAiCompatiblePrefillsItsBaseUrl() =
        runScreenTest {
            setScreenContent { AiSettingsSection() }

            onNodeWithText("OpenAI-compatible").performClick()

            input(AI_BASE_URL_TAG).assertTextEquals("https://api.openai.com/v1")
            input(AI_MODEL_TAG).assertTextEquals("")
        }

    @Test
    fun switchingProviderResetsUrlAndClearsKeyAndModel() =
        runScreenTest {
            setScreenContent { AiSettingsSection() }
            onNodeWithText("Anthropic").performClick()
            input(AI_API_KEY_TAG).performTextInput("sk-ant-1")

            onNodeWithText("OpenAI-compatible").performClick()

            input(AI_BASE_URL_TAG).assertTextEquals("https://api.openai.com/v1")
            input(AI_API_KEY_TAG).assertTextEquals("")
            input(AI_MODEL_TAG).assertTextEquals("")
        }

    @Test
    fun editingOnlyTheBaseUrlKeepsTheKey() =
        runScreenTest {
            setScreenContent { AiSettingsSection() }
            onNodeWithText("OpenAI-compatible").performClick()
            input(AI_API_KEY_TAG).performTextInput("sk-or-1")
            input(AI_MODEL_TAG).performTextInput("openai/gpt-5")

            input(AI_BASE_URL_TAG).performTextClearance()
            input(AI_BASE_URL_TAG).performTextInput("https://openrouter.ai/api/v1")
            onNodeWithText("Save").performClick()

            awaitSaved { it != null }
            val saved = savedConfig()!!
            assertEquals("https://openrouter.ai/api/v1", saved.baseUrl.value)
            assertEquals(Secret("sk-or-1"), saved.apiKey)
        }

    @Test
    fun anthropicSaveWithoutKeyShowsAnErrorAndSavesNothing() =
        runScreenTest {
            setScreenContent { AiSettingsSection() }
            onNodeWithText("Anthropic").performClick()

            onNodeWithText("Save").performClick()

            awaitText("This provider needs an API key.")
            assertNull(savedConfig())
        }

    @Test
    fun invalidBaseUrlShowsAnError() =
        runScreenTest {
            setScreenContent { AiSettingsSection() }
            onNodeWithText("OpenAI-compatible").performClick()
            input(AI_MODEL_TAG).performTextInput("llama3")
            input(AI_BASE_URL_TAG).performTextClearance()
            input(AI_BASE_URL_TAG).performTextInput("localhost:11434")

            onNodeWithText("Save").performClick()

            awaitText("Enter an http:// or https:// base URL.")
            assertNull(savedConfig())
        }

    @Test
    fun anthropicSaveWithKeyPersistsTheConfig() =
        runScreenTest {
            setScreenContent { AiSettingsSection() }
            onNodeWithText("Anthropic").performClick()
            input(AI_API_KEY_TAG).performTextInput("sk-ant-1")

            onNodeWithText("Save").performClick()

            awaitSaved { it != null }
            assertEquals(
                LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "sk-ant-1", "claude-opus-5-5"),
                savedConfig(),
            )
        }

    @Test
    fun clearingTheKeyOnOpenAiCompatibleSaves() =
        runScreenTest {
            runBlocking {
                repository.save(LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, "http://localhost:11434/v1", "old-key", "llama3"))
            }
            setScreenContent { AiSettingsSection() }
            awaitText("llama3")

            input(AI_API_KEY_TAG).performTextClearance()
            onNodeWithText("Save").performClick()

            awaitSaved { it?.apiKey == null }
            assertEquals("http://localhost:11434/v1", savedConfig()!!.baseUrl.value)
        }

    @Test
    fun noneAndSaveDeletesTheWholeConfig() =
        runScreenTest {
            runBlocking {
                repository.save(LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "sk-ant-1", "claude-opus-5-5"))
            }
            setScreenContent { AiSettingsSection() }
            awaitText("https://api.anthropic.com/v1")

            onNodeWithText("None").performClick()
            onNodeWithText("Save").performClick()

            awaitSaved { it == null }
        }

    @Test
    fun savedConfigIsShownOnOpen() =
        runScreenTest {
            runBlocking {
                repository.save(LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, "http://localhost:1234/v1", "", "qwen3"))
            }
            setScreenContent { AiSettingsSection() }

            awaitText("qwen3")
            input(AI_BASE_URL_TAG).assertTextEquals("http://localhost:1234/v1")
        }
}

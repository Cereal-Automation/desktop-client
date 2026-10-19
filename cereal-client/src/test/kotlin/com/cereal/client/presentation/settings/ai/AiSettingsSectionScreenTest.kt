package com.cereal.client.presentation.settings.ai

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.domain.provider.LlmProvider
import com.cereal.client.domain.repository.LlmProviderConfigRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryLlmProvider
import com.cereal.client.presentation.settings.ApplicationSettingsScreen
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Settings → AI on the in-memory repository graph: the preset tiles, what the form shows for each,
 * and what Save persists. Self-skips on headless CI via the screen harness.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class AiSettingsSectionScreenTest {
    private val repository: LlmProviderConfigRepository
        get() = KoinJavaComponent.get(LlmProviderConfigRepository::class.java)

    private val llmProvider: InMemoryLlmProvider
        get() = KoinJavaComponent.get(LlmProvider::class.java) as InMemoryLlmProvider

    private fun savedConfig(): LlmProviderConfig? = runBlocking { repository.get() }

    private fun save(config: LlmProviderConfig) = runBlocking { repository.save(config) }

    private fun ComposeUiTest.input(tag: String): SemanticsNodeInteraction = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    private fun ComposeUiTest.awaitText(text: String) = waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.awaitSaved(predicate: (LlmProviderConfig?) -> Boolean) = waitUntil(timeoutMillis = 5_000) { predicate(savedConfig()) }

    @Test
    fun settingsScreenShowsAiSectionAfterNotifications() =
        runScreenTest {
            setScreenContent { ApplicationSettingsScreen() }

            onNode(hasScrollAction()).performScrollToNode(hasText("Google Gemini"))
            onNodeWithText("AI").assertIsDisplayed()
        }

    @Test
    fun showsTheSixPresetTilesWithNoneSelected() =
        runScreenTest {
            setScreenContent { AiSettingsSection() }

            listOf("Google Gemini", "Anthropic", "OpenAI", "OpenRouter", "Local model", "Other").forEach {
                onNodeWithText(it).assertIsDisplayed().assertIsNotSelected()
            }
            onNodeWithTag(AI_API_KEY_TAG).assertDoesNotExist()
        }

    @Test
    fun selectingAPresetShowsItsKeyLinkAndModelAndSavesItsBaseUrl() =
        runScreenTest {
            setScreenContent { AiSettingsSection() }

            onNodeWithText("OpenRouter").performClick()

            onNodeWithText("OpenRouter").assertIsSelected()
            onNodeWithText("Get a key ↗").assertIsDisplayed()
            onNodeWithText("anthropic/claude-opus-5.5", substring = true).assertIsDisplayed()
            onNodeWithTag(AI_BASE_URL_TAG).assertDoesNotExist()

            input(AI_API_KEY_TAG).performTextInput("sk-or-1")
            onNodeWithText("Save").performClick()

            awaitSaved { it != null }
            assertEquals(
                LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1", "sk-or-1", "anthropic/claude-opus-5.5"),
                savedConfig(),
            )
        }

    @Test
    fun changeRevealsTheModelField() =
        runScreenTest {
            setScreenContent { AiSettingsSection() }
            onNodeWithText("Anthropic").performClick()
            onNodeWithTag(AI_MODEL_TAG).assertDoesNotExist()

            onNodeWithText("Change").performClick()

            input(AI_MODEL_TAG).assertTextEquals("claude-opus-5-5")
        }

    @Test
    fun localShowsAServerFieldAndNoKeyField() =
        runScreenTest {
            setScreenContent { AiSettingsSection() }

            onNodeWithText("Local model").performClick()

            onNodeWithText("Server").assertIsDisplayed()
            input(AI_BASE_URL_TAG).assertTextEquals("http://localhost:11434/v1")
            onNodeWithTag(AI_API_KEY_TAG).assertDoesNotExist()
            onNodeWithText("Get a key ↗").assertDoesNotExist()
        }

    @Test
    fun eachPresetShowsItsOwnSpendLimitHintAndLocalShowsNone() =
        runScreenTest {
            setScreenContent { AiSettingsSection() }

            onNodeWithText("Google Gemini").performClick()
            onNodeWithText("Google Cloud Billing", substring = true).assertIsDisplayed()

            onNodeWithText("OpenAI").performClick()
            onNodeWithText("OpenAI usage limits", substring = true).assertIsDisplayed()
            onNodeWithText("Google Cloud Billing", substring = true).assertDoesNotExist()

            onNodeWithText("OpenRouter").performClick()
            onNodeWithText("OpenRouter keys", substring = true).assertIsDisplayed()

            onNodeWithText("Local model").performClick()
            onNodeWithText("spend limit", substring = true).assertDoesNotExist()
            onNodeWithText("OpenRouter keys", substring = true).assertDoesNotExist()
        }

    @Test
    fun presetsShowThePrivacyLine() =
        runScreenTest {
            setScreenContent { AiSettingsSection() }

            onNodeWithText("Google Gemini").performClick()

            onNodeWithText("Nothing passes through Cereal", substring = true).assertIsDisplayed()

            onNodeWithText("Anthropic").performClick()
            onNodeWithText("Nothing passes through Cereal", substring = true).assertIsDisplayed()
        }

    @Test
    fun switchingBetweenPresetsOfTheSameTypeClearsTheKey() =
        runScreenTest {
            setScreenContent { AiSettingsSection() }
            onNodeWithText("OpenAI").performClick()
            input(AI_API_KEY_TAG).performTextInput("sk-openai-1")

            onNodeWithText("Google Gemini").performClick()

            input(AI_API_KEY_TAG).assertTextEquals("")
            input(AI_API_KEY_TAG).performTextInput("gemini-key")
            onNodeWithText("Save").performClick()
            awaitSaved { it != null }
            assertEquals(
                LlmProviderConfig.of(
                    LlmProviderType.OPENAI_COMPATIBLE,
                    "https://generativelanguage.googleapis.com/v1beta/openai",
                    "gemini-key",
                    "gemini-3.8-flash",
                ),
                savedConfig(),
            )
        }

    @Test
    fun aSavedConfigOpensOnItsMatchingPreset() =
        runScreenTest {
            save(LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, "http://localhost:1234/v1", "", "qwen3"))
            setScreenContent { AiSettingsSection() }

            awaitText("qwen3")
            onNodeWithText("Local model").assertIsSelected()
            input(AI_BASE_URL_TAG).assertTextEquals("http://localhost:1234/v1")
        }

    @Test
    fun aSavedConfigWithAnUnknownHostOpensOnOther() =
        runScreenTest {
            save(LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, "https://proxy.example.com/v1", "k", "my-model"))
            setScreenContent { AiSettingsSection() }

            awaitText("my-model")
            onNodeWithText("Other").assertIsSelected()
            input(AI_BASE_URL_TAG).assertTextEquals("https://proxy.example.com/v1")
        }

    @Test
    fun anthropicBehindAProxyShowsItsBaseUrlAndSavesTheEdit() =
        runScreenTest {
            save(LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://proxy.example.com/v1", "sk-ant-1", "claude-opus-5-5"))
            setScreenContent { AiSettingsSection() }
            awaitText("claude-opus-5-5")

            onNodeWithText("Anthropic").assertIsSelected()
            input(AI_BASE_URL_TAG).assertTextEquals("https://proxy.example.com/v1")

            input(AI_BASE_URL_TAG).performTextReplacement("https://proxy2.example.com/v1")
            onNodeWithText("Save").performClick()

            awaitSaved { it?.baseUrl?.value == "https://proxy2.example.com/v1" }
            assertEquals(
                LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://proxy2.example.com/v1", "sk-ant-1", "claude-opus-5-5"),
                savedConfig(),
            )
        }

    @Test
    fun anthropicOnItsOwnHostHidesTheBaseUrl() =
        runScreenTest {
            save(LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "sk-ant-1", "claude-opus-5-5"))
            setScreenContent { AiSettingsSection() }
            awaitText("claude-opus-5-5")

            onNodeWithTag(AI_BASE_URL_TAG).assertDoesNotExist()
        }

    @Test
    fun removeDeletesTheWholeConfig() =
        runScreenTest {
            save(LlmProviderConfig.of(LlmProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "sk-ant-1", "claude-opus-5-5"))
            setScreenContent { AiSettingsSection() }
            awaitText("claude-opus-5-5")

            onNodeWithText("Remove").performClick()

            awaitSaved { it == null }
            onNodeWithText("Anthropic").assertIsNotSelected()
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
    fun otherWithAnInvalidBaseUrlShowsAnError() =
        runScreenTest {
            setScreenContent { AiSettingsSection() }
            onNodeWithText("Other").performClick()
            input(AI_MODEL_TAG).performTextInput("llama3")
            input(AI_BASE_URL_TAG).performTextInput("localhost:11434")

            onNodeWithText("Save").performClick()

            awaitText("Enter an http:// or https:// base URL.")
            assertNull(savedConfig())
        }

    @Test
    fun testConnectionReportsTheModelCountAndKeepsTheDefaultModel() =
        runScreenTest {
            llmProvider.models = listOf(LlmModel("claude-opus-5-5"), LlmModel("claude-haiku-4-5"))
            setScreenContent { AiSettingsSection() }
            onNodeWithText("Anthropic").performClick()
            input(AI_API_KEY_TAG).performTextInput("sk-ant-1")

            onNodeWithText("Test connection").performClick()

            awaitText("Connected · 2 models")
            onNodeWithText("claude-opus-5-5", substring = true).assertIsDisplayed()
        }

    @Test
    fun theModelListFiltersAsYouTypeAndAPickFillsTheField() =
        runScreenTest {
            llmProvider.models = listOf(LlmModel("gpt-5"), LlmModel("gpt-5-mini"), LlmModel("llama3"))
            setScreenContent { AiSettingsSection() }
            onNodeWithText("Local model").performClick()
            onNodeWithText("Test connection").performClick()
            awaitText("Connected · 3 models")

            onNodeWithTag(AI_MODEL_OPEN_TAG).performClick()
            onNodeWithText("llama3").assertIsDisplayed()
            input(AI_MODEL_TAG).performTextInput("mini")
            onNodeWithText("llama3").assertDoesNotExist()
            onNodeWithText("gpt-5-mini").performClick()

            input(AI_MODEL_TAG).assertTextEquals("gpt-5-mini")
        }

    @Test
    fun withoutAFetchedListTheModelIsPlainText() =
        runScreenTest {
            setScreenContent { AiSettingsSection() }
            onNodeWithText("Other").performClick()

            onNodeWithTag(AI_MODEL_OPEN_TAG).assertDoesNotExist()
            input(AI_MODEL_TAG).performTextInput("any-model-id")
            input(AI_MODEL_TAG).assertTextEquals("any-model-id")
        }

    @Test
    fun openingASavedProviderFetchesTheListSilently() =
        runScreenTest {
            llmProvider.models = listOf(LlmModel("qwen3"), LlmModel("llama3"))
            save(LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, "http://localhost:1234/v1", "", "qwen3"))
            setScreenContent { AiSettingsSection() }

            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag(AI_MODEL_OPEN_TAG)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Connected", substring = true).assertDoesNotExist()
        }
}

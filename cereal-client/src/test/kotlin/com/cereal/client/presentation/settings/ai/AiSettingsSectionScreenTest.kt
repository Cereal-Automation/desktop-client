package com.cereal.client.presentation.settings.ai

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
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
import com.cereal.client.domain.model.exception.LlmCallException
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
import java.time.Instant
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
            llmProvider.models =
                listOf(
                    LlmModel("openrouter/auto", created = Instant.parse("2026-02-01T00:00:00Z")),
                    LlmModel("meta/llama-free", created = Instant.parse("2025-01-01T00:00:00Z"), pricedFree = true),
                )
            setScreenContent { AiSettingsSection() }

            onNodeWithText("OpenRouter").performClick()

            onNodeWithText("OpenRouter").assertIsSelected()
            onNodeWithText("Get a key ↗").assertIsDisplayed()
            onNodeWithText("Chosen after you add your key").assertIsDisplayed()
            onNodeWithTag(AI_BASE_URL_TAG).assertDoesNotExist()

            input(AI_API_KEY_TAG).performTextInput("sk-or-1")
            // The default: the newest free model, never the newer router.
            awaitText("meta/llama-free")
            onNodeWithText("Save").performClick()

            awaitSaved { it != null }
            assertEquals(
                LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1", "sk-or-1", "meta/llama-free"),
                savedConfig(),
            )
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
            input(AI_BASE_URL_TAG).performTextInput("localhost:11434")

            onNodeWithText("Save").performClick()

            awaitText("Enter an http:// or https:// base URL.")
            assertNull(savedConfig())
        }

    @Test
    fun testConnectionReportsTheModelCountAndTheNewestModelIsSelected() =
        runScreenTest {
            llmProvider.models =
                listOf(
                    LlmModel("claude-haiku-4-5", created = Instant.parse("2025-10-01T00:00:00Z")),
                    LlmModel("claude-opus-5-5", created = Instant.parse("2026-05-01T00:00:00Z")),
                )
            setScreenContent { AiSettingsSection() }
            onNodeWithText("Anthropic").performClick()
            input(AI_API_KEY_TAG).performTextInput("sk-ant-1")

            onNodeWithText("Test connection").performClick()

            awaitText("Connected · 2 models")
            awaitText("claude-opus-5-5")
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

    private fun ComposeUiTest.awaitTag(tag: String) = waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.row(text: String): SemanticsNodeInteraction = onNode(hasText(text) and hasAnyAncestor(hasTestTag(AI_MODEL_LIST_TAG)))

    @Test
    fun beforeTheConnectionIsUsableTheModelWaitsForTheKeyOrServer() =
        runScreenTest {
            setScreenContent { AiSettingsSection() }

            onNodeWithText("Anthropic").performClick()
            onNodeWithText("Chosen after you add your key").assertIsDisplayed()
            onNodeWithTag(AI_MODEL_OPEN_TAG).assertDoesNotExist()

            onNodeWithText("Other").performClick()
            onNodeWithText("Chosen after you add the server address").assertIsDisplayed()
        }

    @Test
    fun whileTheListLoadsTheModelShowsLoading() =
        runScreenTest {
            llmProvider.listModelsLatencyMillis = 60_000
            setScreenContent { AiSettingsSection() }

            onNodeWithText("Local model").performClick()

            awaitText("Loading models…")
            onNodeWithTag(AI_MODEL_OPEN_TAG).assertDoesNotExist()
        }

    @Test
    fun aLoadedListOpensWithSearchAndAPickClosesItAndIsSaved() =
        runScreenTest {
            llmProvider.models = listOf(LlmModel("gpt-5"), LlmModel("gpt-5-mini"), LlmModel("llama3"))
            setScreenContent { AiSettingsSection() }
            onNodeWithText("Local model").performClick()
            // Local's default server address is a valid connection, so the list loads on its own.
            awaitTag(AI_MODEL_OPEN_TAG)

            onNodeWithTag(AI_MODEL_OPEN_TAG).performClick()
            row("llama3").assertIsDisplayed()
            input(AI_MODEL_SEARCH_TAG).performTextInput("MINI")
            row("llama3").assertDoesNotExist()
            row("gpt-5-mini").performClick()

            onNodeWithTag(AI_MODEL_LIST_TAG).assertDoesNotExist()
            onNodeWithText("gpt-5-mini").assertIsDisplayed()
            onNodeWithText("Save").performClick()
            awaitSaved { it?.model == "gpt-5-mini" }
        }

    @Test
    fun searchAlsoMatchesTheDisplayName() =
        runScreenTest {
            llmProvider.models = listOf(LlmModel("m-1", displayName = "Fancy Model"), LlmModel("m-2", displayName = "Plain Model"))
            setScreenContent { AiSettingsSection() }
            onNodeWithText("Local model").performClick()
            awaitTag(AI_MODEL_OPEN_TAG)

            onNodeWithTag(AI_MODEL_OPEN_TAG).performClick()
            input(AI_MODEL_SEARCH_TAG).performTextInput("fancy")

            row("m-1").assertIsDisplayed()
            row("m-2").assertDoesNotExist()
        }

    @Test
    fun localRowsShowNameOverIdAFreeBadgeAndThePullDate() =
        runScreenTest {
            llmProvider.models = listOf(LlmModel("qwen3:8b", displayName = "Qwen3 8B", created = Instant.parse("2026-03-04T12:00:00Z")))
            save(LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, "http://localhost:11434/v1", "", "qwen3:8b"))
            setScreenContent { AiSettingsSection() }
            awaitTag(AI_MODEL_OPEN_TAG)

            // The closed field: name over id, and the badge.
            onNodeWithTag(AI_MODEL_OPEN_TAG).assert(hasText("Qwen3 8B")).assert(hasText("qwen3:8b")).assert(hasText("Free"))

            onNodeWithTag(AI_MODEL_OPEN_TAG).performClick()
            row("Qwen3 8B").assertIsDisplayed()
            row("qwen3:8b").assertIsDisplayed()
            row("Free").assertIsDisplayed()
            row("Pulled 2026-03-04").assertIsDisplayed()
        }

    @Test
    fun onOpenRouterOnlyPricedFreeModelsShowFreeAndDatesArePlain() =
        runScreenTest {
            llmProvider.models =
                listOf(
                    LlmModel("paid/model", created = Instant.parse("2026-02-01T12:00:00Z")),
                    LlmModel("free/model", created = Instant.parse("2026-01-01T12:00:00Z"), pricedFree = true),
                )
            save(LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1", "sk-or-1", "paid/model"))
            setScreenContent { AiSettingsSection() }
            awaitTag(AI_MODEL_OPEN_TAG)

            onNodeWithTag(AI_MODEL_OPEN_TAG).assert(hasText("paid/model")).assert(!hasText("Free"))
            onNodeWithTag(AI_MODEL_OPEN_TAG).performClick()
            onAllNodes(hasText("Free") and hasAnyAncestor(hasTestTag(AI_MODEL_LIST_TAG))).assertCountEquals(1)
            row("2026-02-01").assertIsDisplayed()
            row("2026-01-01").assertIsDisplayed()
        }

    @Test
    fun aListWithoutDatesShowsNoDateColumn() =
        runScreenTest {
            llmProvider.models = listOf(LlmModel("qwen3"), LlmModel("llama3"))
            save(LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, "http://localhost:11434/v1", "", "qwen3"))
            setScreenContent { AiSettingsSection() }
            awaitTag(AI_MODEL_OPEN_TAG)

            onNodeWithTag(AI_MODEL_OPEN_TAG).performClick()
            row("llama3").assertIsDisplayed()
            onNode(hasText("Pulled", substring = true)).assertDoesNotExist()
        }

    @Test
    fun aSavedIdMissingFromTheListStaysSelectedWithAWarning() =
        runScreenTest {
            llmProvider.models = listOf(LlmModel("qwen3"), LlmModel("llama3"))
            save(LlmProviderConfig.of(LlmProviderType.OPENAI_COMPATIBLE, "http://localhost:1234/v1", "", "gemini-3.8-flash"))
            setScreenContent { AiSettingsSection() }

            awaitText("Not offered by provider")
            onNodeWithText("gemini-3.8-flash").assertIsDisplayed()
            onNodeWithText("Save").performClick()
            awaitSaved { it?.model == "gemini-3.8-flash" }
        }

    @Test
    fun aFailedLoadFallsBackToFreeTextAndRetryReloads() =
        runScreenTest {
            llmProvider.listModelsError = LlmCallException.Authentication()
            setScreenContent { AiSettingsSection() }
            onNodeWithText("Local model").performClick()

            awaitText("Retry")
            onNodeWithText("Bad API key", substring = true).assertIsDisplayed()
            input(AI_MODEL_TAG).performTextReplacement("typed-model")
            input(AI_MODEL_TAG).assertTextEquals("typed-model")

            llmProvider.listModelsError = null
            onNodeWithText("Retry").performClick()

            awaitTag(AI_MODEL_OPEN_TAG)
            onNodeWithText("Retry").assertDoesNotExist()
        }
}

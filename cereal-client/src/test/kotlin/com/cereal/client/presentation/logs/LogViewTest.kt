package com.cereal.client.presentation.logs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cereal.client.domain.model.artifact.Artifact
import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.logging.LoggingPriority
import testutil.runScreenTest
import testutil.setScreenContent
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Exercises the pure [LogOutputView] component: that supplied [LoggingEvent]s render, that the
 * filter tabs are present, that clicking the clear control invokes [LogOutputView]'s `onClear`
 * callback, and that selecting a priority filter tab hides non-matching events.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class LogViewTest {
    private fun logEvent(
        priority: LoggingPriority,
        message: String,
    ) = LoggingEvent(
        priority = priority,
        tag = "test",
        message = message,
        timestamp = Date(0),
    )

    private val sampleEvents =
        listOf(
            logEvent(LoggingPriority.INFO, "info-line-message"),
            logEvent(LoggingPriority.WARNING, "warning-line-message"),
            logEvent(LoggingPriority.ERROR, "error-line-message"),
        )

    @OptIn(ExperimentalTime::class)
    private fun artifact(
        id: String,
        name: String = "$id.csv",
    ) = Artifact(
        id = id,
        taskId = "task",
        name = name,
        mimeType = "text/csv",
        sizeBytes = 10,
        createdAt = Instant.fromEpochMilliseconds(0),
    )

    @Test
    fun rendersEventsAndFilterTabs() =
        runScreenTest {
            setScreenContent {
                LogOutputView(events = sampleEvents, onClear = {})
            }

            onNodeWithText("Output").assertIsDisplayed()

            // Filter tabs.
            onNodeWithText("ALL").assertIsDisplayed()
            onNodeWithText("INFO").assertIsDisplayed()
            onNodeWithText("WARN").assertIsDisplayed()
            onNodeWithText("ERR").assertIsDisplayed()

            // Auto-scroll control.
            onNodeWithText("Auto-scroll").assertIsDisplayed()

            // Every event message renders while the ALL filter is active.
            onNodeWithText("info-line-message").assertExists()
            onNodeWithText("warning-line-message").assertExists()
            onNodeWithText("error-line-message").assertExists()
        }

    @Test
    fun clickingClearInvokesCallback() =
        runScreenTest {
            var cleared = false
            setScreenContent {
                LogOutputView(events = sampleEvents, onClear = { cleared = true })
            }

            onNodeWithTag(CLEAR_BUTTON_TEST_TAG).performClick()

            assertTrue(cleared, "onClear callback should fire when the clear button is clicked")
        }

    @Test
    fun selectingErrorFilterHidesOtherPriorities() =
        runScreenTest {
            setScreenContent {
                LogOutputView(events = sampleEvents, onClear = {})
            }

            // Sanity: all messages visible before filtering.
            onNodeWithText("info-line-message").assertExists()

            onNodeWithText("ERR").performClick()
            waitForIdle()

            // Only the error event should remain rendered.
            onNodeWithText("error-line-message").assertExists()
            onNodeWithText("info-line-message").assertDoesNotExist()
            onNodeWithText("warning-line-message").assertDoesNotExist()
        }

    @Test
    fun showsUnseenBadgeWhileNotViewingArtifacts() =
        runScreenTest {
            setScreenContent {
                LogOutputView(
                    events = emptyList(),
                    onClear = {},
                    artifacts = listOf(artifact("a"), artifact("b")),
                )
            }

            // Drawer opens on the Logs tab, so the two artifacts are unseen and the badge shows "2".
            // The pill's clickable merges descendant semantics, so query the badge in the unmerged tree.
            onNodeWithTag(ARTIFACT_BADGE_TEST_TAG, useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("2", useUnmergedTree = true).assertExists()
        }

    @Test
    fun viewingArtifactsClearsBadge() =
        runScreenTest {
            setScreenContent {
                LogOutputView(
                    events = emptyList(),
                    onClear = {},
                    artifacts = listOf(artifact("a"), artifact("b")),
                )
            }

            onNodeWithTag(ARTIFACT_BADGE_TEST_TAG, useUnmergedTree = true).assertExists()

            // Selecting the Artifacts tab reveals the list; the artifacts become seen and the badge clears.
            onNodeWithText("Artifacts").performClick()
            waitForIdle()

            onNodeWithText("a.csv").assertExists()
            onNodeWithTag(ARTIFACT_BADGE_TEST_TAG, useUnmergedTree = true).assertDoesNotExist()
        }

    @Test
    fun badgeReappearsForArtifactsArrivingAfterViewing() =
        runScreenTest {
            var artifacts by mutableStateOf(listOf(artifact("a"), artifact("b")))
            setScreenContent {
                LogOutputView(events = emptyList(), onClear = {}, artifacts = artifacts)
            }

            // View artifacts to clear the badge, then return to the Logs tab.
            onNodeWithText("Artifacts").performClick()
            waitForIdle()
            onNodeWithTag(ARTIFACT_BADGE_TEST_TAG, useUnmergedTree = true).assertDoesNotExist()

            onNodeWithText("Logs").performClick()
            waitForIdle()

            // A new artifact arrives while the user is not looking — the badge returns showing "1".
            artifacts = artifacts + artifact("c")
            waitForIdle()

            onNodeWithTag(ARTIFACT_BADGE_TEST_TAG, useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("1", useUnmergedTree = true).assertExists()
        }

    @Test
    fun clickingArtifactsTabWhileCollapsedExpandsDrawer() =
        runScreenTest {
            setScreenContent {
                LogOutputView(
                    events = emptyList(),
                    onClear = {},
                    artifacts = listOf(artifact("a", name = "report.csv")),
                )
            }

            // Collapse the drawer; its content (including the artifact list) is no longer rendered.
            onNodeWithText("Output").performClick()
            waitForIdle()
            onNodeWithText("report.csv").assertDoesNotExist()

            // Clicking the Artifacts tab while collapsed should both select it and expand the drawer.
            onNodeWithText("Artifacts").performClick()
            waitForIdle()

            onNodeWithText("report.csv").assertExists()
        }
}

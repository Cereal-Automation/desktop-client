package com.cereal.client.presentation.logs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The inspector's [TaskLogSection] and [TaskArtifactsSection]. */
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
    fun rendersEventsWithFilterAndControls() =
        runScreenTest {
            setScreenContent { TaskLogSection(events = sampleEvents, onClear = {}) }

            onNodeWithText("LOG").assertIsDisplayed()
            onNodeWithText("ALL").assertIsDisplayed()
            onNodeWithText("INFO").assertIsDisplayed()
            onNodeWithText("WARN").assertIsDisplayed()
            onNodeWithText("ERR").assertIsDisplayed()
            onNodeWithText("Auto-scroll").assertIsDisplayed()
            onNodeWithContentDescription("Copy all logs").assertExists()
            onNodeWithContentDescription("Clear").assertExists()

            onNodeWithText("info-line-message").assertExists()
            onNodeWithText("warning-line-message").assertExists()
            onNodeWithText("error-line-message").assertExists()
        }

    @Test
    fun showsNoOutputYetWhenEmpty() =
        runScreenTest {
            setScreenContent { TaskLogSection(events = emptyList(), onClear = {}) }

            onNodeWithText("No output yet").assertIsDisplayed()
        }

    @Test
    fun clickingClearInvokesCallback() =
        runScreenTest {
            var cleared = false
            setScreenContent { TaskLogSection(events = sampleEvents, onClear = { cleared = true }) }

            onNodeWithTag(CLEAR_BUTTON_TEST_TAG).performClick()

            assertTrue(cleared, "onClear callback should fire when the clear button is clicked")
        }

    @Test
    fun selectingErrorFilterHidesOtherPriorities() =
        runScreenTest {
            setScreenContent { TaskLogSection(events = sampleEvents, onClear = {}) }

            onNodeWithText("ERR").performClick()
            waitForIdle()

            onNodeWithText("error-line-message").assertExists()
            onNodeWithText("info-line-message").assertDoesNotExist()
            onNodeWithText("warning-line-message").assertDoesNotExist()
        }

    @Test
    fun listsArtifactsAndDownloadsTheClickedOne() =
        runScreenTest {
            var downloaded: Artifact? = null
            val receipt = artifact("a", name = "receipt.csv")
            setScreenContent { TaskArtifactsSection(artifacts = listOf(receipt), onDownloadArtifact = { downloaded = it }) }

            onNodeWithText("ARTIFACTS").assertIsDisplayed()
            onNodeWithText("receipt.csv").assertIsDisplayed()
            onNodeWithContentDescription("Download").performClick()

            assertEquals(receipt, downloaded)
        }

    @Test
    fun showsNoneYetWithoutArtifacts() =
        runScreenTest {
            setScreenContent { TaskArtifactsSection(artifacts = emptyList(), onDownloadArtifact = {}) }

            onNodeWithText("None yet").assertIsDisplayed()
        }
}

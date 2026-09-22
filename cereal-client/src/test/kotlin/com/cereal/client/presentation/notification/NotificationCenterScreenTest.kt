package com.cereal.client.presentation.notification

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.model.notification.NotificationChannelType
import com.cereal.client.domain.model.notification.NotificationDeliveryStatus
import com.cereal.client.domain.repository.NotificationHistoryRepository
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Renders [NotificationCenterScreen] on the in-memory repository graph. Covers cross-task
 * aggregation, the empty state, expanding a row to reveal per-channel delivery status, and the
 * "Open task" navigate intent. Self-skips on headless CI via the screen harness.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class NotificationCenterScreenTest {
    private fun seed(
        taskId: String,
        title: String,
        message: String,
        timestamp: Long,
        attempts: List<ChannelAttempt>,
    ) {
        val repository = KoinJavaComponent.get<NotificationHistoryRepository>(NotificationHistoryRepository::class.java)
        runBlocking { repository.record(taskId, title, message, timestamp, attempts) }
    }

    private fun success(channel: NotificationChannelType) = ChannelAttempt(channel, NotificationDeliveryStatus.SUCCESS, payload = "ok", errorMessage = null)

    @Test
    fun rendersNotificationsFromAllTasks() =
        runScreenTest {
            seed("task-a", "Item in stock", "Found it", 300L, listOf(success(NotificationChannelType.DISCORD)))
            seed("task-b", "Run finished", "All done", 200L, listOf(success(NotificationChannelType.EMAIL)))

            setScreenContent { NotificationCenterScreen() }

            waitUntil(timeoutMillis = 5_000) {
                onAllNodes(hasText("Item in stock")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Item in stock").assertIsDisplayed()
            onNodeWithText("Run finished").assertIsDisplayed()
        }

    @Test
    fun showsEmptyStateWhenThereAreNoNotifications() =
        runScreenTest {
            setScreenContent { NotificationCenterScreen() }

            waitUntil(timeoutMillis = 5_000) {
                onAllNodes(hasText("No notifications yet")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("No notifications yet").assertIsDisplayed()
        }

    @Test
    fun expandingARowRevealsPerChannelDeliveryStatus() =
        runScreenTest {
            seed("task-a", "Item in stock", "Found it", 300L, listOf(success(NotificationChannelType.DISCORD)))

            setScreenContent { NotificationCenterScreen() }

            waitUntil(timeoutMillis = 5_000) {
                onAllNodes(hasText("Item in stock")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Item in stock").performClick()

            waitUntil(timeoutMillis = 5_000) {
                onAllNodes(hasText("Delivered")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Discord").assertIsDisplayed()
            onNodeWithText("Delivered").assertIsDisplayed()
        }

    @Test
    fun openTaskActionEmitsTheSourceTaskId() =
        runScreenTest {
            seed("task-a", "Item in stock", "Found it", 300L, listOf(success(NotificationChannelType.DISCORD)))
            var openedTaskId: String? = null

            setScreenContent { NotificationCenterScreen(onOpenTask = { openedTaskId = it }) }

            waitUntil(timeoutMillis = 5_000) {
                onAllNodes(hasText("Item in stock")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Item in stock").performClick()

            waitUntil(timeoutMillis = 5_000) {
                onAllNodes(hasText("Open task")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Open task").performClick()
            waitForIdle()

            assertEquals("task-a", openedTaskId)
        }
}

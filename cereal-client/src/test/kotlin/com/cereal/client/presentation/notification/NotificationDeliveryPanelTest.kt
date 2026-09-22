package com.cereal.client.presentation.notification

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.cereal.client.domain.model.notification.NotificationChannelType
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test

/**
 * Renders [NotificationDeliveryPanel] directly. The "no channel configured" case cannot be produced
 * through the recording pipeline (a notification with zero attempts is never persisted), so it is
 * verified at the composable seam: an empty attempt list shows the calm "not delivered" notice,
 * while a failed attempt shows its error.
 */
@OptIn(ExperimentalTestApi::class)
class NotificationDeliveryPanelTest {
    @Test
    fun showsNotDeliveredNoticeWhenThereAreNoChannels() =
        runScreenTest {
            setScreenContent {
                NotificationDeliveryPanel(attempts = emptyList(), taskName = "Walmart PS5", onOpenTask = {})
            }

            onNodeWithText("Not delivered to any channel").assertIsDisplayed()
        }

    @Test
    fun showsErrorMessageForAFailedChannel() =
        runScreenTest {
            setScreenContent {
                NotificationDeliveryPanel(
                    attempts =
                        listOf(
                            NotificationDeliveryUiModel(
                                id = "1",
                                channel = NotificationChannelType.EMAIL,
                                delivered = false,
                                errorMessage = "SMTP 535 authentication failed",
                            ),
                        ),
                    taskName = "Nike Restock",
                    onOpenTask = {},
                )
            }

            onNodeWithText("Email").assertIsDisplayed()
            onNodeWithText("Failed").assertIsDisplayed()
        }
}

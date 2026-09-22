package com.cereal.client.presentation.feedback

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders [feedbackView] with a visible [FeedbackAction.Success]. Asserts the snackbar shows the
 * message and the "Close" action label, and that the action's dismiss callback runs once the
 * snackbar has been shown.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class FeedbackViewTest {
    @Test
    fun rendersFeedbackSnackbarAndDismisses() =
        runScreenTest {
            var dismissed = false
            val message = "Your changes were saved"
            val feedbackAction =
                FeedbackAction.Success(message) { dismissed = true }

            setScreenContent { feedbackView(feedbackAction) }

            onNodeWithText(message).assertIsDisplayed()
            onNodeWithText("Close").assertIsDisplayed()

            onNodeWithText("Close").performClick()

            waitUntil { dismissed }
            assertTrue(dismissed, "Clicking Close should invoke the feedback action's dismiss callback")
        }
}

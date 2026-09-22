package com.cereal.client.presentation.error

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders [errorView] with a visible [ErrorAction.Message]. Asserts the dialog shows the "Error"
 * title and the error message, then clicks "Close" and verifies the action's dismiss callback runs.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class ErrorViewTest {
    @Test
    fun rendersErrorAndDismissesOnClose() =
        runScreenTest {
            var dismissed = false
            val errorMessage = "Something went wrong while syncing"
            val errorAction =
                mutableStateOf<ErrorAction>(
                    ErrorAction.Message(errorMessage) { dismissed = true },
                )

            setScreenContent { errorView(errorAction) }

            onNodeWithText("Error").assertIsDisplayed()
            onNodeWithText(errorMessage).assertIsDisplayed()

            onNodeWithText("Close").performClick()

            assertTrue(dismissed, "Clicking Close should invoke the error action's dismiss callback")
        }
}

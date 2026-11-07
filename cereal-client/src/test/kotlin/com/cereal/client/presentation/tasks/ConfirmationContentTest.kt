package com.cereal.client.presentation.tasks

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.sdk.component.userinteraction.ConfirmationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class, ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class ConfirmationContentTest {
    private val now = Instant.fromEpochSeconds(1_800_000_000)

    private fun aConfirmation(expiresAt: Instant? = now + 14.minutes + 56.seconds) =
        UserInteraction.Confirmation(
            title = "Checkout",
            question = "Place order on amazon.com?",
            details = listOf("Clicks <b>Place your order</b>.", "Max order total is \$350.00."),
            url = "https://www.amazon.com/checkout",
            confirmLabel = "Yes, place order",
            declineLabel = "No, stop here",
            expiresAt = expiresAt,
        )

    @Test
    fun showsTheQuestionDetailsLinkAndCountdownAsPlainText() =
        runScreenTest {
            setScreenContent { ConfirmationContent(aConfirmation(), now) }

            onNodeWithText("Place order on amazon.com?").assertIsDisplayed()
            onNodeWithText("Clicks <b>Place your order</b>.").assertIsDisplayed()
            onNodeWithText("Max order total is \$350.00.").assertIsDisplayed()
            onNodeWithText("https://www.amazon.com/checkout").assertIsDisplayed()
            onNodeWithText("Expires in 14:56 · then counts as No, stop here").assertIsDisplayed()
        }

    @Test
    fun noExpiryShowsNoCountdown() =
        runScreenTest {
            setScreenContent { ConfirmationContent(aConfirmation(expiresAt = null), now) }

            onNodeWithText("Expires in", substring = true).assertDoesNotExist()
        }

    @Test
    fun confirmAnswersConfirmed() =
        runScreenTest {
            val confirmation = aConfirmation()
            setScreenContent { ConfirmationContent(confirmation, now) }

            onNodeWithText("Yes, place order").performClick()

            assertEquals(ConfirmationResult.Confirmed, confirmation.answer.getCompleted())
        }

    @Test
    fun declineAnswersDeclined() =
        runScreenTest {
            val confirmation = aConfirmation()
            setScreenContent { ConfirmationContent(confirmation, now) }

            onNodeWithText("No, stop here").performClick()

            assertEquals(ConfirmationResult.Declined, confirmation.answer.getCompleted())
        }
}

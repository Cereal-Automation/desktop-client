package com.cereal.client.presentation.authenticate.forgotpassword

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.koin.java.KoinJavaComponent
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test

/**
 * Renders [ForgotPasswordScreen] on in-memory repositories. Resolves the real
 * [ForgotPasswordViewModel] from Koin (as the production screen does) and passes noop callbacks.
 * Covers a render smoke check plus interaction: typing the email and submitting.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class ForgotPasswordScreenTest {
    private fun viewModel(): ForgotPasswordViewModel = KoinJavaComponent.get(ForgotPasswordViewModel::class.java)

    /** Targets the editable [androidx.compose.foundation.text.BasicTextField] nested under [tag]. */
    private fun ComposeUiTest.editableFieldUnder(tag: String): SemanticsNodeInteraction = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    @Test
    fun rendersForgotPasswordForm() =
        runScreenTest {
            setScreenContent {
                ForgotPasswordScreen(
                    forgotPasswordViewModel = viewModel(),
                    onNavigateToLogin = {},
                    onSuccess = {},
                )
            }

            onNodeWithText("Enter your email address and we’ll send you a link to reset your password.")
                .assertIsDisplayed()
            // Field label is rendered uppercased by AuthTextField.
            onNodeWithText("EMAIL ADDRESS").assertIsDisplayed()
            // Submit button.
            onNodeWithText("Send Reset Link").assertIsDisplayed()
            // Footer navigation back to login.
            onNodeWithText("Back to Sign In").assertIsDisplayed()
        }

    @Test
    fun typingEmailUpdatesViewModel() =
        runScreenTest {
            val forgotPasswordViewModel = viewModel()
            setScreenContent {
                ForgotPasswordScreen(
                    forgotPasswordViewModel = forgotPasswordViewModel,
                    onNavigateToLogin = {},
                    onSuccess = {},
                )
            }

            editableFieldUnder(ForgotPasswordTestTags.EMAIL_FIELD).performTextInput("ada@example.com")

            waitForIdle()

            assertEquals("ada@example.com", forgotPasswordViewModel.email.value)
        }

    @Test
    fun submitButtonIsClickable() =
        runScreenTest {
            setScreenContent {
                ForgotPasswordScreen(
                    forgotPasswordViewModel = viewModel(),
                    onNavigateToLogin = {},
                    onSuccess = {},
                )
            }

            editableFieldUnder(ForgotPasswordTestTags.EMAIL_FIELD).performTextInput("ada@example.com")
            onNodeWithTag(ForgotPasswordTestTags.SUBMIT_BUTTON).assertIsDisplayed().performClick()
            waitForIdle()
        }

    @Test
    fun backToLoginFooterNavigatesBack() =
        runScreenTest {
            var navigatedToLogin = false
            setScreenContent {
                ForgotPasswordScreen(
                    forgotPasswordViewModel = viewModel(),
                    onNavigateToLogin = { navigatedToLogin = true },
                    onSuccess = {},
                )
            }

            onNodeWithText("Back to Sign In").performClick()
            waitForIdle()

            assertTrue(navigatedToLogin)
        }
}

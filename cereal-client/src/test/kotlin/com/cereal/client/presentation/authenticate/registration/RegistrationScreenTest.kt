package com.cereal.client.presentation.authenticate.registration

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
 * Renders [RegistrationScreen] on in-memory repositories. Resolves the real [RegistrationViewModel]
 * from Koin (as the production screen does) and passes a noop navigation callback. Covers a render
 * smoke check plus interaction: typing into the form fields and submitting.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class RegistrationScreenTest {
    private fun viewModel(): RegistrationViewModel = KoinJavaComponent.get(RegistrationViewModel::class.java)

    /** Targets the editable [androidx.compose.foundation.text.BasicTextField] nested under [tag]. */
    private fun ComposeUiTest.editableFieldUnder(tag: String): SemanticsNodeInteraction = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    @Test
    fun rendersRegistrationForm() =
        runScreenTest {
            setScreenContent {
                RegistrationScreen(
                    registrationViewModel = viewModel(),
                    onNavigateToLogin = {},
                )
            }

            // Field labels are rendered uppercased by AuthTextField.
            onNodeWithText("NAME").assertIsDisplayed()
            onNodeWithText("EMAIL ADDRESS").assertIsDisplayed()
            onNodeWithText("PASSWORD").assertIsDisplayed()
            // Submit button.
            onNodeWithText("Create account").assertIsDisplayed()
            // Footer navigation back to login.
            onNodeWithText("Login").assertIsDisplayed()
        }

    @Test
    fun typingIntoFieldsUpdatesViewModel() =
        runScreenTest {
            val registrationViewModel = viewModel()
            setScreenContent {
                RegistrationScreen(
                    registrationViewModel = registrationViewModel,
                    onNavigateToLogin = {},
                )
            }

            editableFieldUnder(RegistrationTestTags.NAME_FIELD).performTextInput("Ada Lovelace")
            editableFieldUnder(RegistrationTestTags.EMAIL_FIELD).performTextInput("ada@example.com")
            editableFieldUnder(RegistrationTestTags.PASSWORD_FIELD).performTextInput("Sup3rStr0ng!Pass")

            waitForIdle()

            assertEquals("Ada Lovelace", registrationViewModel.name.value)
            assertEquals("ada@example.com", registrationViewModel.username.value)
            assertEquals("Sup3rStr0ng!Pass", registrationViewModel.password.value)
        }

    @Test
    fun submitButtonIsClickable() =
        runScreenTest {
            setScreenContent {
                RegistrationScreen(
                    registrationViewModel = viewModel(),
                    onNavigateToLogin = {},
                )
            }

            onNodeWithTag(RegistrationTestTags.SUBMIT_BUTTON).assertIsDisplayed().performClick()
            waitForIdle()
        }

    @Test
    fun loginFooterNavigatesBack() =
        runScreenTest {
            var navigatedToLogin = false
            setScreenContent {
                RegistrationScreen(
                    registrationViewModel = viewModel(),
                    onNavigateToLogin = { navigatedToLogin = true },
                )
            }

            onNodeWithText("Login").performClick()
            waitForIdle()

            assertTrue(navigatedToLogin)
        }
}

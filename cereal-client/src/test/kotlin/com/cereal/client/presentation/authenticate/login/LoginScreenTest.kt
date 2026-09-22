package com.cereal.client.presentation.authenticate.login

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test

/**
 * Renders [LoginScreen] on the in-memory repository graph supplied by the harness. Resolves the real
 * [LoginViewModel] (a Koin `factory` that takes a coroutine scope) and exercises the email/password
 * fields, the submit button and the registration/forgot-password navigation links.
 *
 * The form labels are rendered upper-cased by the design system (`AuthTextField`), so the asserted
 * strings differ in case from the raw `Res.string` values.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class LoginScreenTest {
    @Test
    fun rendersLoginForm() =
        runScreenTest {
            setScreenContent { LoginScreenUnderTest() }

            onNodeWithText("EMAIL ADDRESS").assertIsDisplayed()
            onNodeWithText("PASSWORD").assertIsDisplayed()
            onNodeWithText("Sign In").assertIsDisplayed()
            onNodeWithText("Forgot password?").assertIsDisplayed()
        }

    @Test
    fun rendersRegistrationFooterLink() =
        runScreenTest {
            setScreenContent { LoginScreenUnderTest() }

            // The footer label is rendered with a trailing space, so match on a substring.
            onNodeWithText("New to Cereal?", substring = true).assertExists()
            onNodeWithText("Create an account").assertExists()
        }

    @Test
    fun acceptsEmailInput() =
        runScreenTest {
            setScreenContent { LoginScreenUnderTest() }

            // The email field is the first editable text field on the screen.
            onAllNodes(hasSetTextAction())[0].performTextInput("tester@example.com")

            onNodeWithText("tester@example.com", substring = true).assertExists()
        }

    @Test
    fun acceptsPasswordInputAndSubmits() =
        runScreenTest {
            setScreenContent { LoginScreenUnderTest() }

            // The password field is the second editable text field on the screen.
            onAllNodes(hasSetTextAction())[1].performTextInput("hunter2")

            // The submit button stays present and clickable after entering credentials.
            onNodeWithText("Sign In").performClick()
            onNodeWithText("Sign In").assertExists()
        }

    @Test
    fun navigationLinksAreClickable() =
        runScreenTest {
            setScreenContent { LoginScreenUnderTest() }

            onNodeWithText("Forgot password?").performClick()
            onNodeWithText("Create an account").performClick()

            onNodeWithText("Forgot password?").assertExists()
        }
}

@Composable
private fun LoginScreenUnderTest() {
    val scope = rememberCoroutineScope()
    val loginViewModel =
        remember {
            KoinJavaComponent.get<LoginViewModel>(
                LoginViewModel::class.java,
                parameters = { parametersOf(scope) },
            )
        }
    LoginScreen(
        loginViewModel = loginViewModel,
        onNavigateToRegister = {},
        onNavigateToForgotPassword = {},
    )
}

package com.cereal.client.presentation.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Renders [GuestProfileScreen] directly with stub callbacks. Verifies the guest call-to-action and
 * locked-feature copy is shown, and that the Login / Create Account buttons invoke their callbacks.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class GuestProfileScreenTest {
    @Test
    fun rendersGuestSections() =
        runScreenTest {
            setScreenContent {
                GuestProfileScreen(onLoginClick = {}, onCreateAccountClick = {})
            }

            onNodeWithText("Guest Profile").assertIsDisplayed()
            onNodeWithText("Create account").assertIsDisplayed()
            onNodeWithText("Sign in").assertIsDisplayed()
            // The locked-features card sits below the fold; assert it exists in the tree.
            onNodeWithText("Locked features".uppercase()).assertExists()
            onNodeWithText("Premium scripts").assertExists()
            onNodeWithText("Cloud sync").assertExists()
            onNodeWithText("Community").assertExists()
        }

    @Test
    fun loginButtonInvokesCallback() =
        runScreenTest {
            var loginClicked = false
            var createAccountClicked = false
            setScreenContent {
                GuestProfileScreen(
                    onLoginClick = { loginClicked = true },
                    onCreateAccountClick = { createAccountClicked = true },
                )
            }

            onNodeWithText("Sign in").performClick()

            assertEquals(true, loginClicked)
            assertFalse(createAccountClicked)
        }

    @Test
    fun createAccountButtonInvokesCallback() =
        runScreenTest {
            var loginClicked = false
            var createAccountClicked = false
            setScreenContent {
                GuestProfileScreen(
                    onLoginClick = { loginClicked = true },
                    onCreateAccountClick = { createAccountClicked = true },
                )
            }

            onNodeWithText("Create account").performClick()

            assertEquals(true, createAccountClicked)
            assertFalse(loginClicked)
        }
}

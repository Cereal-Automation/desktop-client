package com.cereal.client.presentation.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test

/**
 * Renders the top-level [ProfileScreen], which resolves its own [ProfileViewModel] and
 * [ApplicationConfig] from the in-memory Koin graph. The in-memory user repository is seeded with a
 * guest user, so the screen delegates to [GuestProfileScreen] and shows the guest call-to-action.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class ProfileScreenTest {
    @Test
    fun rendersProfileScreen() =
        runScreenTest {
            setScreenContent { ProfileScreen() }

            onNodeWithText("Guest Profile").assertIsDisplayed()
            onNodeWithText("Create Account").assertIsDisplayed()
            onNodeWithText("Sign In").assertIsDisplayed()
        }
}

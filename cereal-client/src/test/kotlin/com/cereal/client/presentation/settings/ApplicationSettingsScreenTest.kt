package com.cereal.client.presentation.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test

/**
 * Renders [ApplicationSettingsScreen] entirely on fake repositories. The settings screen depends on
 * repositories that previously had no fakes (ApplicationPreference / Notification / System), so this
 * is the headline case the harness unblocks.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class ApplicationSettingsScreenTest {
    @Test
    fun rendersSettingsSections() =
        runScreenTest {
            setScreenContent { ApplicationSettingsScreen() }

            onNodeWithText("General").assertIsDisplayed()
            onNodeWithText("Notifications").assertIsDisplayed()
        }
}

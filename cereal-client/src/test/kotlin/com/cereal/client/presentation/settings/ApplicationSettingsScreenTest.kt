package com.cereal.client.presentation.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onSiblings
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.cereal.client.domain.repository.GoalSharingRepository
import org.koin.java.KoinJavaComponent
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertFalse

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

    @Test
    fun `the goal sharing toggle starts on and flips the stored choice`() =
        runScreenTest {
            setScreenContent { ApplicationSettingsScreen() }
            val title = "Share my goals to help improve Cereal"
            onNode(hasScrollToNodeAction()).performScrollToNode(hasText(title))
            // The Privacy card lists crash reporting, then goal sharing.
            val toggle = onNodeWithText(title).onSiblings().filter(isToggleable()).onLast()

            toggle.assertIsOn()
            toggle.performClick()

            toggle.assertIsOff()
            waitUntil { !KoinJavaComponent.get<GoalSharingRepository>(GoalSharingRepository::class.java).isEnabled() }
            assertFalse(KoinJavaComponent.get<GoalSharingRepository>(GoalSharingRepository::class.java).isEnabled())
        }
}

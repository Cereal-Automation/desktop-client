package com.cereal.client.presentation.bootstrap

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test

/**
 * Renders [BootstrapScreen] on in-memory repositories. The in-memory
 * `InMemoryApplicationRepository` reports the installed version as both the latest and the minimum
 * required version, so `checkForUpdates()` resolves to `UpToDate`. That means the bootstrap flow
 * never emits a `BootstrapState.Interrupted`, and the "Update Required" / "Update Available" update
 * dialogs are unreachable in this configuration. This test therefore asserts the always-present
 * splash content (the app icon) renders.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class BootstrapScreenTest {
    @Test
    fun rendersBootstrapSplash() =
        runScreenTest {
            setScreenContent { BootstrapScreen(onCompleted = {}) }

            // The app icon image is shown for the entire bootstrap progress and carries the
            // "App Icon" content description (Res.string.app_icon).
            onNodeWithContentDescription("App Icon").assertIsDisplayed()
        }
}

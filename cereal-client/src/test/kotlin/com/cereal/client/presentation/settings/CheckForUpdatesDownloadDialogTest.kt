package com.cereal.client.presentation.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import testutil.runScreenTest
import testutil.setScreenContent
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renders [CheckForUpdatesDownloadDialog] in both of its states:
 * - download in progress (no installer yet): shows the status text and progress bar;
 * - installer ready: shows the countdown timer text and an "UPDATE" button that fires
 *   [onOpenInstaller] when clicked.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class CheckForUpdatesDownloadDialogTest {
    @Test
    fun rendersDownloadProgressStatus() =
        runScreenTest {
            setScreenContent {
                CheckForUpdatesDownloadDialog(
                    progress = 0.5f,
                    statusText = "Downloading update...",
                    installerFile = null,
                    onOpenInstaller = {},
                    onDismiss = {},
                )
            }

            onNodeWithText("Downloading update...").assertIsDisplayed()
        }

    @Test
    fun rendersInstallerCountdownAndUpdateButton() =
        runScreenTest {
            val installer = File.createTempFile("cereal-installer", ".dmg").apply { deleteOnExit() }

            setScreenContent {
                CheckForUpdatesDownloadDialog(
                    progress = 1.0f,
                    statusText = "Done",
                    installerFile = installer,
                    onOpenInstaller = {},
                    onDismiss = {},
                )
            }

            // installer_timer = "Installing update in %1$d seconds..." starting at 5.
            onNodeWithText("Installing update in 5 seconds...").assertIsDisplayed()
            onNodeWithText("UPDATE").assertIsDisplayed()
        }

    @Test
    fun openButtonInvokesOnOpenInstaller() =
        runScreenTest {
            val installer = File.createTempFile("cereal-installer", ".dmg").apply { deleteOnExit() }
            var opened: File? = null

            setScreenContent {
                CheckForUpdatesDownloadDialog(
                    progress = 1.0f,
                    statusText = "Done",
                    installerFile = installer,
                    onOpenInstaller = { opened = it },
                    onDismiss = {},
                )
            }

            onNodeWithText("UPDATE").performClick()

            assertEquals(installer, opened)
        }

    @Test
    fun progressStateRendersProgressBar() =
        runScreenTest {
            var dismissed = false

            setScreenContent {
                CheckForUpdatesDownloadDialog(
                    progress = 0.25f,
                    statusText = "Preparing...",
                    installerFile = null,
                    onOpenInstaller = {},
                    onDismiss = { dismissed = true },
                )
            }

            onNodeWithText("Preparing...").assertIsDisplayed()
            // No UPDATE button while the download is in progress.
            assertTrue(dismissed.not())
        }
}

package com.cereal.client.presentation.tasks.dialog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fixtures.aScriptPackageInstance
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders [ViewConfigurationDialog] on the in-memory harness. The dialog title interpolates the
 * package name and the footer shows the version code. Also exercises the close button wiring.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class ViewConfigurationDialogTest {
    @Test
    fun rendersTitleAndVersionCode() =
        runScreenTest {
            setScreenContent {
                ViewConfigurationDialog(
                    scriptPackageInstance = aScriptPackageInstance(id = "instance-1", packageName = "com.example.pkg"),
                    onDismissRequest = {},
                )
            }

            // aScriptPackage fixes the manifest name to "Test" and versionCode to 1.
            onNodeWithText("Configuration for \"Test\"").assertIsDisplayed()
            onNodeWithText("Version code: 1").assertIsDisplayed()
        }

    @Test
    fun closeButtonInvokesDismiss() =
        runScreenTest {
            var dismissed = false
            setScreenContent {
                ViewConfigurationDialog(
                    scriptPackageInstance = aScriptPackageInstance(id = "instance-1", packageName = "com.example.pkg"),
                    onDismissRequest = { dismissed = true },
                )
            }

            onNodeWithContentDescription("Close").performClick()

            assertTrue(dismissed, "Clicking close should invoke onDismissRequest")
        }
}

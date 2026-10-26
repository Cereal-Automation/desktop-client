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
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Renders [ViewConfigurationDialog] on the in-memory harness. The dialog title interpolates the
 * package name and the footer shows the setup's creation date and the version code. Also exercises
 * the close button wiring.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class, ExperimentalTime::class)
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
            onNodeWithText("Version code: 1", substring = true).assertIsDisplayed()
        }

    @Test
    fun showsWhenTheTaskSetupWasCreated() {
        val locale = Locale.getDefault()
        val timeZone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val setup =
                aScriptPackageInstance(id = "instance-1", packageName = "com.example.pkg")
                    .copy(createdAt = Instant.parse("2026-10-26T14:03:12Z"))

            runScreenTest {
                setScreenContent { ViewConfigurationDialog(scriptPackageInstance = setup, onDismissRequest = {}) }

                onNodeWithText("Created: Oct 26, 2026, 2:03:12", substring = true).assertIsDisplayed()
            }
        } finally {
            Locale.setDefault(locale)
            TimeZone.setDefault(timeZone)
        }
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

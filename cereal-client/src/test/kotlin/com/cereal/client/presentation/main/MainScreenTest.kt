package com.cereal.client.presentation.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test

/**
 * Renders the full [MainScreen] on the in-memory Koin graph. The in-memory user repository seeds a
 * signed-in user, so the screen resolves to the authenticated shell (sidebar menu + routed content)
 * after the asynchronous authentication flow settles. Covers both render assertions (menu entries
 * are displayed) and interaction (clicking a menu item switches the displayed content).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class MainScreenTest {
    @Test
    fun rendersAuthenticatedShellWithMenuEntries() =
        runScreenTest {
            setScreenContent { MainScreen() }

            // Authentication resolves on a background dispatcher, so wait for the sidebar to render.
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("Tasks").fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithText("WORKSPACE").assertIsDisplayed()
            // "Tasks" appears both in the sidebar and as the default route's header, so assert it is
            // present rather than expecting a single node. The remaining items are sidebar-only here.
            onAllNodesWithText("Tasks").onFirst().assertIsDisplayed()
            onNodeWithText("Marketplace").assertIsDisplayed()
            onNodeWithText("Datasets").assertIsDisplayed()
            onNodeWithText("Settings").assertIsDisplayed()
        }

    @Test
    fun clickingMenuItemSwitchesContent() =
        runScreenTest {
            setScreenContent { MainScreen() }

            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()
            }

            // The settings content ("General") is not shown by the default Tasks route.
            onAllNodesWithText("General").assertCountEquals(0)

            onNodeWithText("Settings").performClick()

            // Navigating to the settings route reveals the settings screen's distinctive content.
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("General").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("General").assertIsDisplayed()
        }
}

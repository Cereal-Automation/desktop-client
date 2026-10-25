package com.cereal.client.presentation.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders [TasksEmptyState] in isolation and verifies its content and that its main button and
 * Home link invoke the supplied callbacks.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class TasksEmptyStateTest {
    @Test
    fun rendersEmptyStateContent() =
        runScreenTest {
            setScreenContent {
                TasksEmptyState(onNewTaskSetup = {}, onNavigateToHome = {})
            }

            onNodeWithText("No task setups yet").assertIsDisplayed()
            onNodeWithText("New task setup").assertIsDisplayed()
            onNodeWithText("or describe a goal on Home").assertIsDisplayed()
            onNodeWithText("Create group").assertDoesNotExist()
        }

    @Test
    fun clickingNewTaskSetupFiresCallback() =
        runScreenTest {
            var clicked = false
            setScreenContent {
                TasksEmptyState(onNewTaskSetup = { clicked = true }, onNavigateToHome = {})
            }

            onNodeWithText("New task setup").performClick()

            assertTrue(clicked, "onNewTaskSetup should be invoked when New task setup is clicked")
        }

    @Test
    fun clickingHomeLinkFiresCallback() =
        runScreenTest {
            var clicked = false
            setScreenContent {
                TasksEmptyState(onNewTaskSetup = {}, onNavigateToHome = { clicked = true })
            }

            onNodeWithText("or describe a goal on Home").performClick()

            assertTrue(clicked, "onNavigateToHome should be invoked when the Home link is clicked")
        }
}

package com.cereal.client.presentation.view.fields

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.cereal.client.presentation.view.fields.state.StringTextFieldState
import com.cereal.client.presentation.view.fields.state.SwitchFieldState
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Render + input tests for the field composables that take a direct state argument:
 * [TextField] (label render + [performTextInput] propagation to state) and [SwitchField]
 * (toggle propagation to state).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class FieldsUiTest {
    @Test
    fun textFieldRendersLabel() =
        runScreenTest {
            val state = StringTextFieldState(initialValue = "")

            setScreenContent {
                TextField(title = "Username", state = state)
            }

            onNodeWithText("Username").assertIsDisplayed()
        }

    @Test
    fun typingIntoTextFieldUpdatesState() =
        runScreenTest {
            val state = StringTextFieldState(initialValue = "")

            setScreenContent {
                TextField(title = "Username", state = state)
            }

            onNodeWithText("Username").performTextInput("hello")

            assertEquals("hello", state.text)
        }

    @Test
    fun togglingSwitchUpdatesState() =
        runScreenTest {
            val state = SwitchFieldState(initialValue = false)

            setScreenContent {
                SwitchField(state = state)
            }

            onNode(isToggleable()).assertIsOff()
            onNode(isToggleable()).performClick()

            assertTrue(state.isChecked)
            onNode(isToggleable()).assertIsOn()
        }
}

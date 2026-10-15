package com.cereal.client.presentation.view.fields

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cereal.client.application.exception.FieldOptionsTimeoutException
import com.cereal.client.application.exception.ScriptCallException
import com.cereal.client.presentation.view.fields.state.OptionsLoadStatus
import com.cereal.client.presentation.view.fields.state.OptionsTextFieldState
import com.cereal.sdk.FieldOption
import com.cereal.sdk.NoFieldOptions
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class OptionsTextFieldTest {
    @Test
    fun `rows show the label with the id beneath, or the id alone, and picking one stores the id`() =
        runScreenTest {
            val state = OptionsTextFieldState(NoFieldOptions)
            state.options = listOf(FieldOption("acc-1", "Main account"), FieldOption("acc-2"))
            setScreenContent { OptionsTextField("Account", state, onLoadOptions = {}) }

            onNodeWithTag(OPTIONS_FIELD_OPEN_TAG).performClick()

            onNodeWithText("Main account").assertIsDisplayed()
            onNodeWithText("acc-1").assertIsDisplayed()
            onNodeWithText("acc-2").assertIsDisplayed()

            onNodeWithText("Main account").performClick()
            assertEquals("acc-1", state.text)
        }

    @Test
    fun `opening the dropdown asks for options and refresh forces a reload`() =
        runScreenTest {
            val requests = mutableListOf<Boolean>()
            setScreenContent { OptionsTextField("Account", OptionsTextFieldState(NoFieldOptions), onLoadOptions = { requests += it }) }

            onNodeWithTag(OPTIONS_FIELD_OPEN_TAG).performClick()
            onNodeWithTag(OPTIONS_FIELD_REFRESH_TAG).performClick()

            assertEquals(listOf(false, true), requests)
        }

    @Test
    fun `a failure shows its message with a retry`() =
        runScreenTest {
            val requests = mutableListOf<Boolean>()
            val state = OptionsTextFieldState(NoFieldOptions)
            state.loadStatus = OptionsLoadStatus.Failed(ScriptCallException(IllegalStateException("Invalid API key")))
            setScreenContent { OptionsTextField("Account", state, onLoadOptions = { requests += it }) }

            onNodeWithText("Invalid API key").assertIsDisplayed()
            onNodeWithText("Retry").performClick()

            assertEquals(listOf(true), requests)
        }

    @Test
    fun `a timeout shows the localized timeout text`() =
        runScreenTest {
            val state = OptionsTextFieldState(NoFieldOptions)
            state.loadStatus = OptionsLoadStatus.Failed(FieldOptionsTimeoutException(15.seconds))
            setScreenContent { OptionsTextField("Account", state, onLoadOptions = {}) }

            onNodeWithText("Loading the options took longer than 15 seconds.").assertIsDisplayed()
        }
}

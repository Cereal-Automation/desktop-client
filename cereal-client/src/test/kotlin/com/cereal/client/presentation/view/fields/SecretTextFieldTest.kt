package com.cereal.client.presentation.view.fields

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.client.presentation.tasks.script.overview.configuration.model.ConfigurationItemsFormSection
import com.cereal.client.presentation.view.fields.state.SecretTextFieldState
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The one new test seam in the secret feature: masked input is the guarantee users actually *see*,
 * and the configuration item mapper test only asserts which field state is produced — not how it
 * behaves. Without this, the reveal toggle has no coverage at all.
 *
 * Note on what is assertable: Compose's `EditableText` semantics carry the *untransformed* text, so a
 * `onNodeWithText` assertion cannot tell masked from revealed. The observable consequences of the
 * toggle are the reveal state itself and the control's own label flipping between reveal and hide, so
 * those are what these tests assert. That the transformation masks at all is pinned separately below.
 */
@OptIn(ExperimentalTestApi::class)
class SecretTextFieldTest {
    private companion object {
        const val CREDENTIAL = "sk-live-super-sensitive"
        const val REVEAL_LABEL = "Reveal"
        const val HIDE_LABEL = "Hide"
    }

    @Test
    fun `starts hidden, so the credential is never shown without a deliberate act`() =
        runScreenTest {
            val state = SecretTextFieldState(initialValue = CREDENTIAL)

            setScreenContent { SecretTextField("API key", state) }

            assertFalse(state.isRevealed.value)
            onNodeWithContentDescription(REVEAL_LABEL).assertIsDisplayed()
        }

    @Test
    fun `the reveal control is present on a masked field`() =
        runScreenTest {
            setScreenContent { SecretTextField("API key", SecretTextFieldState(initialValue = CREDENTIAL)) }

            onNodeWithTag(SECRET_FIELD_REVEAL_TOGGLE_TAG).assertIsDisplayed()
        }

    @Test
    fun `toggling reveals the plaintext`() =
        runScreenTest {
            val state = SecretTextFieldState(initialValue = CREDENTIAL)
            setScreenContent { SecretTextField("API key", state) }

            onNodeWithTag(SECRET_FIELD_REVEAL_TOGGLE_TAG).performClick()

            assertTrue(state.isRevealed.value)
            // The control now offers to hide, which is only rendered when the field is revealed.
            onNodeWithContentDescription(HIDE_LABEL).assertIsDisplayed()
        }

    @Test
    fun `toggling back re-masks the field`() =
        runScreenTest {
            val state = SecretTextFieldState(initialValue = CREDENTIAL)
            setScreenContent { SecretTextField("API key", state) }

            onNodeWithTag(SECRET_FIELD_REVEAL_TOGGLE_TAG).performClick()
            onNodeWithTag(SECRET_FIELD_REVEAL_TOGGLE_TAG).performClick()

            assertFalse(state.isRevealed.value)
            onNodeWithContentDescription(REVEAL_LABEL).assertIsDisplayed()
        }

    @Test
    fun `revealing one field does not reveal another`() =
        runScreenTest {
            val first = SecretTextFieldState(initialValue = "first-credential")
            val second = SecretTextFieldState(initialValue = "second-credential")

            setScreenContent {
                Column {
                    SecretTextField("First", first)
                    SecretTextField("Second", second)
                }
            }

            onAllNodesWithTag(SECRET_FIELD_REVEAL_TOGGLE_TAG)[0].performClick()

            // Asserted as a count rather than per-field, because node order is not part of the
            // contract — the guarantee is that one click reveals one field, never both.
            assertEquals(
                1,
                listOf(first, second).count { it.isRevealed.value },
                "clicking one reveal control must flip exactly one field's state",
            )
        }

    @Test
    fun `reopening the configuration forgets that a field was revealed`() =
        runScreenTest {
            // Exercises the seam the guarantee actually rests on: closing the configuration screen
            // discards the form section, and reopening builds a new one from the same configuration
            // items. Asserting on a hand-built field state instead would just restate the default.
            val item =
                ConfigurationItem(
                    definition =
                        ScriptConfigurationItemDefinition(
                            name = "API key",
                            description = "A credential",
                            key = "apiKey",
                            position = 0,
                            type = ConfigItemType.SecretConfigItem,
                            isNullable = false,
                            stateModifier = null,
                            isScriptIdentifier = false,
                        ),
                    value = ConfigValue.SecretValue(Secret(CREDENTIAL)),
                )

            fun buildSection() = ConfigurationItemsFormSection(listOf(item)) { _, _, _ -> }

            val firstVisit = buildSection().getFormFieldStates().single() as SecretTextFieldState
            setScreenContent { SecretTextField("API key", firstVisit) }
            onNodeWithTag(SECRET_FIELD_REVEAL_TOGGLE_TAG).performClick()
            assertTrue(firstVisit.isRevealed.value, "precondition: the field was revealed")

            val secondVisit = buildSection().getFormFieldStates().single() as SecretTextFieldState

            assertFalse(secondVisit.isRevealed.value, "reopening the screen must not remember the reveal")
            assertEquals(CREDENTIAL, secondVisit.text, "the credential itself is still pre-filled")
        }

    @Test
    fun `the hidden transformation replaces every character, so nothing of the credential shows`() {
        // Pins the masking itself, which the semantics-based assertions above cannot reach.
        val masked =
            MaskedVisualTransformation()
                .filter(AnnotatedString(CREDENTIAL))
                .text
                .text

        assertEquals(CREDENTIAL.length, masked.length)
        assertFalse(masked.contains("sk-live"))
        assertEquals(1, masked.toSet().size, "expected a single repeated mask character, got '$masked'")
    }

    @Test
    fun `the mask keeps clipboard copy enabled by not being a PasswordVisualTransformation`() {
        // Compose gates the clipboard on the transformation's *type*:
        // `TextFieldSelectionManager.isCopyAllowed` is `hasSelection && !isPassword`, where
        // `isPassword` is `visualTransformation is PasswordVisualTransformation`. Using the stock
        // transformation would silently disable copy, which ticket 04 requires stay enabled — so the
        // type check IS the guarantee, and this test is what stops a "simplification" back to it.
        val transformation: VisualTransformation = MaskedVisualTransformation()

        assertFalse(
            transformation is PasswordVisualTransformation,
            "a PasswordVisualTransformation would make Compose disable clipboard copy on the field",
        )
    }

    @Test
    fun `the mask is the same glyph the stock password transformation uses`() {
        // Not a behavioural requirement, but it keeps credential fields looking like every other
        // masked field in the app rather than subtly different.
        val ours = MaskedVisualTransformation().filter(AnnotatedString(CREDENTIAL)).text.text
        val stock = PasswordVisualTransformation().filter(AnnotatedString(CREDENTIAL)).text.text

        assertEquals(stock, ours)
    }
}

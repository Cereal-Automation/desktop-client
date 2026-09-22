package com.cereal.client.presentation.view.fields

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.VisualTransformation
import com.cereal.client.presentation.view.fields.state.SecretTextFieldState
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.secret_field_hide
import com.cereal_automation.cereal_client.generated.resources.secret_field_reveal
import org.jetbrains.compose.resources.stringResource

/** Test tag on the reveal control, so the Compose test can drive the toggle. */
const val SECRET_FIELD_REVEAL_TOGGLE_TAG: String = "secret_field_reveal_toggle"

/**
 * A credential field: the shared [TextField] with the characters replaced by dots, plus a control to
 * deliberately reveal them — for confirming a pasted token — and hide them again.
 *
 * The masking is purely visual. [SecretTextFieldState.text] always holds the plaintext, which is what
 * validators inspect and what is wrapped into a `Secret` on save.
 *
 * Clipboard copy is deliberately left enabled: suppressing it would block reusing a key in another
 * script's configuration, and anyone who can reach the clipboard can reach the reveal toggle anyway.
 * That is why the mask comes from [MaskedVisualTransformation] rather than the stock
 * `PasswordVisualTransformation`, which Compose treats as a signal to disable copy — see that type.
 */
@Composable
fun SecretTextField(
    title: String,
    state: SecretTextFieldState,
    modifier: Modifier = Modifier,
) {
    val isRevealed by state.isRevealed

    TextField(
        title = title,
        state = state,
        modifier = modifier,
        visualTransformation =
            if (isRevealed) {
                VisualTransformation.None
            } else {
                MaskedVisualTransformation()
            },
        trailingIcon = {
            IconButton(
                onClick = { state.toggleReveal() },
                modifier = Modifier.testTag(SECRET_FIELD_REVEAL_TOGGLE_TAG),
            ) {
                Icon(
                    imageVector = if (isRevealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription =
                        stringResource(
                            if (isRevealed) Res.string.secret_field_hide else Res.string.secret_field_reveal,
                        ),
                )
            }
        },
    )
}

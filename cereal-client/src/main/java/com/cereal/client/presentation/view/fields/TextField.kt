package com.cereal.client.presentation.view.fields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextField
import com.cereal.client.presentation.view.fields.state.TextFieldState

@Composable
fun TextField(
    title: String,
    state: TextFieldState<*>,
    modifier: Modifier = Modifier,
    // The script's placeholder (StateModifier.getPlaceholder) unless the caller supplies its own hint.
    hintText: String = state.placeholder.value.orEmpty(),
    enabled: Boolean = true,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    onImeAction: () -> Unit = {},
    // False when the caller shows [title] above the box itself: the box then carries it only as its
    // accessible name, so screen readers still announce what the input is for.
    labelVisible: Boolean = true,
) {
    FormField(state.error) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(title, visible = labelVisible)
            CerealTextField(
                value = state.text,
                onValueChange = {
                    state.onValueChange(it)
                },
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = visualTransformation,
                trailingIcon = trailingIcon,
                placeholder = hintText.takeIf { it.isNotBlank() },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { if (!labelVisible) contentDescription = title }
                        .onFocusChanged { focusState ->
                            state.onFocusChange(focusState.isFocused)
                        },
                isError = state.showErrors(),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = imeAction),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            onImeAction()
                        },
                    ),
            )
        }
    }
}

/** The caption above a field box; a blank title renders nothing, so a caller that named the field itself gets a bare box. */
@Composable
internal fun FieldLabel(
    title: String,
    visible: Boolean = true,
) {
    if (visible && title.isNotBlank()) {
        CerealText(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = CerealTheme.colorScheme.contentSecondary,
        )
    }
}

package com.cereal.client.presentation.view.fields

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.fields.state.TextFieldState

@Composable
fun TextField(
    title: String,
    state: TextFieldState<*>,
    modifier: Modifier = Modifier,
    hintText: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    onImeAction: () -> Unit = {},
) {
    FormField(state.error) {
        Column(modifier = modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.text,
                onValueChange = {
                    state.onValueChange(it)
                },
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = visualTransformation,
                trailingIcon = trailingIcon,
                placeholder = {
                    CerealText(
                        text = hintText,
                    )
                },
                // A blank title renders no label at all rather than an empty one, so a caller that
                // has already named the field above the input (a single-field list row) gets a bare
                // box instead of a floating label slot holding nothing.
                label =
                    title.takeIf { it.isNotBlank() }?.let {
                        {
                            CerealText(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            state.onFocusChange(focusState.isFocused)
                        },
                textStyle = MaterialTheme.typography.bodyMedium,
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

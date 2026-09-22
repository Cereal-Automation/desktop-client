package com.cereal.client.presentation.view.fields

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import com.cereal.client.presentation.view.fields.state.SwitchFieldState

@Composable
fun SwitchField(
    state: SwitchFieldState,
    modifier: Modifier = Modifier,
) {
    FormField(state.error, modifier) {
        Switch(
            checked = state.fieldValue,
            onCheckedChange = { state.onValueChange(it) },
            modifier =
                Modifier
                    .onFocusChanged { focusState ->
                        state.onFocusChange(focusState.isFocused)
                    },
        )
    }
}

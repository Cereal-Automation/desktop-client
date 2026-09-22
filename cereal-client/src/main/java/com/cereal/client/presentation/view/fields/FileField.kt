package com.cereal.client.presentation.view.fields

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.fields.state.FileFieldState
import com.cereal.client.presentation.view.rememberFileDialogLauncher
import java.awt.FileDialog.LOAD

@Composable
fun FileField(
    state: FileFieldState,
    modifier: Modifier = Modifier,
) {
    val openFileDialog = rememberFileDialogLauncher()
    FormField(state.error, modifier) {
        CerealButton(
            text = "Open",
            onClick = {
                val file = openFileDialog(LOAD)
                if (file != null) {
                    state.onValueChange(file)
                }
            },
            modifier =
                Modifier
                    .onFocusChanged { focusState ->
                        state.onFocusChange(focusState.isFocused)
                    },
        )
    }
}

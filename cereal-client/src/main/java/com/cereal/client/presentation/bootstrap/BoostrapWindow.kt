package com.cereal.client.presentation.bootstrap

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.cereal.client.presentation.view.CerealWindow

@Composable
fun BootstrapWindow(onCompleted: () -> Unit) {
    CerealWindow(
        title = "Loading...",
        state =
            rememberWindowState(
                width = 500.dp,
                height = 300.dp,
                position = WindowPosition.Aligned(Alignment.Center),
            ),
        alwaysOnTop = true,
        undecorated = true,
    ) {
        BootstrapScreen(onCompleted = onCompleted)
    }
}

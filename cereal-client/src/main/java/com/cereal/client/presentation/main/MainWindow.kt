package com.cereal.client.presentation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.cereal.client.application.ApplicationConfig
import com.cereal.client.presentation.view.CerealWindow
import org.koin.compose.koinInject
import java.awt.GraphicsEnvironment

@Composable
fun MainWindow(
    applicationConfig: ApplicationConfig = koinInject(),
    onCloseRequest: () -> Unit,
) {
    // Pre-size and position the window to the screen's maximized area before it is shown. The OS
    // still applies the Maximized placement, but because the floating size already equals the
    // maximized size there is nothing to animate, so the window appears at full size instantly.
    // Without this, macOS animates setExtendedState(MAXIMIZED_BOTH) from the default 800x600,
    // producing a visible "zoom" resize as the main window opens after the boot screen.
    val maxBounds = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
    CerealWindow(
        title = applicationConfig.title,
        state =
            rememberWindowState(
                placement = WindowPlacement.Maximized,
                position = WindowPosition(maxBounds.x.dp, maxBounds.y.dp),
                size = DpSize(maxBounds.width.dp, maxBounds.height.dp),
            ),
        onCloseRequest = onCloseRequest,
    ) {
        MainScreen()
    }
}

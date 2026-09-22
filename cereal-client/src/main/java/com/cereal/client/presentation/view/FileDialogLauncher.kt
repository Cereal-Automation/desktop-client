package com.cereal.client.presentation.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import java.awt.FileDialog
import java.awt.FileDialog.LOAD
import java.awt.Frame
import java.io.File

/**
 * Returns a launcher for the native AWT [FileDialog].
 *
 * Before opening the dialog it clears Compose focus. This works around
 * JetBrains/compose-multiplatform#3892: when the native dialog is confirmed with
 * Enter, that key event is also delivered to the still-focused Compose button
 * that opened it, re-triggering its onClick and immediately reopening the dialog.
 * Clearing focus first means there is no button left to receive the Enter.
 *
 * @param mode one of [FileDialog.LOAD] or [FileDialog.SAVE]; defaults to LOAD.
 * @return the selected [File], or null if the dialog was cancelled.
 */
@Composable
fun rememberFileDialogLauncher(): (mode: Int) -> File? {
    val focusManager = LocalFocusManager.current
    return remember(focusManager) {
        { mode -> openFileDialog(focusManager, mode) }
    }
}

private fun openFileDialog(
    focusManager: FocusManager,
    mode: Int = LOAD,
): File? {
    focusManager.clearFocus(force = true)

    val parentFrame = Frame()
    val fileDialog =
        FileDialog(parentFrame, "Choose a file", mode) // Note: This is a system dialog, string not localized
    fileDialog.isVisible = true
    val file = fileDialog.files?.firstOrNull()
    parentFrame.dispose()

    return file
}

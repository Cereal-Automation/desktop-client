package com.cereal.client.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextButton
import com.cereal.client.presentation.view.LinearProgressBar
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.installer_timer
import com.cereal_automation.cereal_client.generated.resources.update
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import java.io.File

/**
 * Dialog showing download progress when an update is being downloaded.
 * When download finishes, automatically prompts to open the installer after a countdown.
 */
@Composable
fun CheckForUpdatesDownloadDialog(
    progress: Float,
    statusText: String,
    installerFile: File?,
    onOpenInstaller: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    if (installerFile != null) {
        var timer by remember { mutableStateOf(5) }
        LaunchedEffect(timer) {
            if (timer > 0) {
                delay(1000)
                timer -= 1
            } else {
                onOpenInstaller(installerFile)
            }
        }

        AlertDialog(
            modifier = Modifier.widthIn(min = 400.dp),
            onDismissRequest = { /* No-op during installer countdown */ },
            title = { CerealText(stringResource(Res.string.installer_timer, timer)) },
            confirmButton = {
                CerealTextButton(onClick = { onOpenInstaller(installerFile) }) {
                    CerealText(stringResource(Res.string.update).uppercase())
                }
            },
        )
    } else {
        AlertDialog(
            modifier = Modifier.widthIn(min = 400.dp),
            onDismissRequest = onDismiss,
            title = { CerealText(statusText) },
            text = {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressBar(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {},
        )
    }
}

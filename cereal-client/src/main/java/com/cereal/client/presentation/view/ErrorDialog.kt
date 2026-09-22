package com.cereal.client.presentation.view

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.close
import com.cereal_automation.cereal_client.generated.resources.error
import org.jetbrains.compose.resources.stringResource

@Composable
fun ErrorDialog(
    title: String = stringResource(Res.string.error),
    message: String,
    modifier: Modifier = Modifier,
    buttonText: String = stringResource(Res.string.close),
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = modifier.widthIn(min = 400.dp),
        title = {
            CerealText(title)
        },
        text = {
            CerealText(message)
        },
        confirmButton = {
            CerealTextButton(onClick = onDismiss) {
                CerealText(buttonText)
            }
        },
        onDismissRequest = {
            // No-op - force user to click button
        },
    )
}

@Composable
fun UserActionDialog(
    title: String,
    confirmButtonText: String,
    modifier: Modifier = Modifier,
    dismissButtonText: String? = null,
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    AlertDialog(
        modifier = modifier.widthIn(min = 400.dp),
        onDismissRequest = { /* No-op */ },
        title = { CerealText(title) },
        confirmButton = {
            CerealTextButton(onClick = onConfirm) {
                CerealText(confirmButtonText.uppercase())
            }
        },
        dismissButton = {
            if (dismissButtonText != null && onDismiss != null) {
                CerealTextButton(onClick = onDismiss) {
                    CerealText(dismissButtonText.uppercase())
                }
            }
        },
    )
}

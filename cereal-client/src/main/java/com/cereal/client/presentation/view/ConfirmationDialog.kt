package com.cereal.client.presentation.view

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.cancel
import com.cereal_automation.cereal_client.generated.resources.confirm
import org.jetbrains.compose.resources.stringResource

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    confirmButtonText: String = stringResource(Res.string.confirm),
    dismissButtonText: String = stringResource(Res.string.cancel),
    onConfirm: () -> Unit,
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
            CerealTextButton(onClick = onConfirm) {
                CerealText(confirmButtonText)
            }
        },
        dismissButton = {
            CerealTextButton(onClick = onDismiss) {
                CerealText(dismissButtonText)
            }
        },
        onDismissRequest = onDismiss,
    )
}

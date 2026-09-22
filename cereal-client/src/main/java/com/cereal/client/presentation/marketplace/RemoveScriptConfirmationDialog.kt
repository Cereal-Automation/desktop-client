package com.cereal.client.presentation.marketplace

import androidx.compose.runtime.Composable
import com.cereal.client.presentation.view.ConfirmationDialog
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.marketplace_remove
import com.cereal_automation.cereal_client.generated.resources.marketplace_remove_confirm_body
import com.cereal_automation.cereal_client.generated.resources.marketplace_remove_confirm_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun RemoveScriptConfirmationDialog(
    scriptName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmationDialog(
        title = stringResource(Res.string.marketplace_remove_confirm_title, scriptName),
        message = stringResource(Res.string.marketplace_remove_confirm_body),
        confirmButtonText = stringResource(Res.string.marketplace_remove),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

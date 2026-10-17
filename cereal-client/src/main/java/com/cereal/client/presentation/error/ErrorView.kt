package com.cereal.client.presentation.error

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextButton
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.close
import com.cereal_automation.cereal_client.generated.resources.error
import org.jetbrains.compose.resources.stringResource

@Composable
fun errorView(errorAction: State<ErrorAction>) {
    (errorAction.value as? ErrorAction.Message)?.let {
        AlertDialog(
            modifier = Modifier.widthIn(min = 400.dp),
            title = {
                CerealText(stringResource(Res.string.error))
            },
            text = {
                CerealText(it.message)
            },
            confirmButton = {
                CerealTextButton(onClick = { it.dismiss() }) {
                    CerealText(stringResource(Res.string.close))
                }
            },
            onDismissRequest = {
                // No-op
            },
        )
    }
}

package com.cereal.client.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.close
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dialog(
    title: String,
    modifier: Modifier =
        Modifier
            .width(600.dp)
            .height(500.dp)
            .padding(horizontal = 6.dp),
    onDismissRequest: (() -> Unit),
    dismissButtonEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Popup(
        alignment = Alignment.Center,
        properties =
            PopupProperties(
                focusable = true,
                dismissOnClickOutside = false,
            ),
        onDismissRequest = onDismissRequest,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            CerealCard(
                modifier = modifier,
            ) {
                Column {
                    TopAppBar(
                        actions = {
                            CerealIconButton(
                                onClick = { onDismissRequest() },
                                enabled = dismissButtonEnabled,
                            ) {
                                Icon(Icons.Filled.Close, stringResource(Res.string.close))
                            }
                        },
                        title = {
                            CerealText(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        },
                    )
                    Row(modifier = Modifier.weight(1f)) {
                        content()
                    }
                }
            }
        }
    }
}

package com.cereal.client.presentation.view.group

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.tasks.MenuOption
import com.cereal.client.presentation.view.CerealIconButton
import com.cereal.client.presentation.view.CerealText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.more_options
import org.jetbrains.compose.resources.stringResource

/** A "···" button opening a menu of [options], each shown as its [label]. */
@Composable
internal fun MoreOptionsMenu(
    options: List<MenuOption>,
    onOptionSelected: (MenuOption) -> Unit,
    tint: Color,
    label: @Composable (MenuOption) -> String = { stringResource(it.stringResource) },
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CerealIconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = stringResource(Res.string.more_options),
                modifier = Modifier.size(18.dp),
                tint = tint,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { CerealText(label(option)) },
                    onClick = {
                        expanded = false
                        onOptionSelected(option)
                    },
                )
            }
        }
    }
}

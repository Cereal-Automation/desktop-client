package com.cereal.client.presentation.view.fields

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.fields.state.OptionsTextFieldState
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.options_field_open
import com.cereal_automation.cereal_client.generated.resources.options_field_refresh
import com.cereal_automation.cereal_client.generated.resources.options_field_retry
import org.jetbrains.compose.resources.stringResource

const val OPTIONS_FIELD_OPEN_TAG: String = "options_field_open"
const val OPTIONS_FIELD_REFRESH_TAG: String = "options_field_refresh"

/**
 * An editable combo box: free text, plus suggestions the script loads. [onLoadOptions] is invoked with
 * `false` when the dropdown opens (a cached result may be reused) and `true` on refresh or retry.
 */
@Composable
fun OptionsTextField(
    title: String,
    state: OptionsTextFieldState,
    onLoadOptions: (refresh: Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        TextField(
            title = title,
            state = state,
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { onLoadOptions(true) }, modifier = Modifier.testTag(OPTIONS_FIELD_REFRESH_TAG)) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.options_field_refresh))
                        }
                    }
                    IconButton(
                        onClick = {
                            expanded = !expanded
                            if (expanded) onLoadOptions(false)
                        },
                        modifier = Modifier.testTag(OPTIONS_FIELD_OPEN_TAG),
                    ) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(Res.string.options_field_open))
                    }
                }
            },
        )

        state.failure?.let { failure ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CerealText(failure, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { onLoadOptions(true) }) {
                    CerealText(stringResource(Res.string.options_field_retry))
                }
            }
        }

        val options = state.options.orEmpty()
        // A picked id shows every option again; other text filters by label or id.
        val visible =
            if (options.any { it.id == state.text }) {
                options
            } else {
                options.filter {
                    it.id.contains(state.text, ignoreCase = true) || it.label?.contains(state.text, ignoreCase = true) == true
                }
            }
        DropdownMenu(
            expanded = expanded && visible.isNotEmpty(),
            onDismissRequest = { expanded = false },
            // Not focusable, so typing in the field keeps filtering while the menu is open.
            properties = PopupProperties(focusable = false),
        ) {
            visible.forEach { option ->
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        state.onValueChange(option.id)
                    },
                    text = {
                        Column {
                            CerealText(option.label ?: option.id, style = MaterialTheme.typography.bodyMedium)
                            if (option.label != null) {
                                CerealText(option.id, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                )
            }
        }
    }
}

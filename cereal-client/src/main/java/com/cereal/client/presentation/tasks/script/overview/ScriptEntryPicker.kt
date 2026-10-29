package com.cereal.client.presentation.tasks.script.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextField
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.script_entry_count
import com.cereal_automation.cereal_client.generated.resources.script_entry_label
import com.cereal_automation.cereal_client.generated.resources.script_entry_no_match
import com.cereal_automation.cereal_client.generated.resources.script_entry_search
import org.jetbrains.compose.resources.stringResource

/**
 * Picks which entry of a package to configure: its default entry or one of its variants. Disabled when the entry is
 * fixed (editing or duplicating a setup). The search box only appears once the list is long enough to need it.
 */
@Composable
fun ScriptEntryPicker(
    options: List<ScriptEntryOption>,
    selected: ScriptEntryOption,
    enabled: Boolean,
    onSelect: (ScriptEntryOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val close = {
        open = false
        query = ""
    }
    val label = stringResource(Res.string.script_entry_label)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CerealText(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            )
            CerealText(
                text = stringResource(Res.string.script_entry_count, options.size),
                style = MaterialTheme.typography.bodySmall,
                color = CerealTheme.colorScheme.contentTertiary,
            )
        }
        Box {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { open = true }
                        .semantics { contentDescription = label }
                        .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CerealText(
                    text = selected.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else CerealTheme.colorScheme.contentTertiary,
                )
                Spacer(Modifier.weight(1f))
                if (enabled) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = CerealTheme.colorScheme.contentTertiary)
                }
            }
            DropdownMenu(expanded = open, onDismissRequest = close) {
                if (options.size > SEARCH_THRESHOLD) {
                    CerealTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(Res.string.script_entry_search),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                val matches = options.filter { it.name.contains(query.trim(), ignoreCase = true) }
                matches.forEach { option ->
                    DropdownMenuItem(
                        text = { CerealText(option.name) },
                        trailingIcon = {
                            if (option == selected) Icon(Icons.Default.Check, contentDescription = null)
                        },
                        onClick = {
                            close()
                            onSelect(option)
                        },
                    )
                }
                if (matches.isEmpty()) {
                    CerealText(
                        text = stringResource(Res.string.script_entry_no_match, query),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = CerealTheme.colorScheme.contentTertiary,
                    )
                }
            }
        }
    }
}

// Scrolling a short list beats typing into it; search only pays off past this many entries.
private const val SEARCH_THRESHOLD = 6

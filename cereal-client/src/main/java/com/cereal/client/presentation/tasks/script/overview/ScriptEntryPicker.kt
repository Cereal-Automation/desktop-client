package com.cereal.client.presentation.tasks.script.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextField
import com.cereal.client.presentation.view.InputBg
import com.cereal.client.presentation.view.InputBorder
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.script_entry_count
import com.cereal_automation.cereal_client.generated.resources.script_entry_label
import com.cereal_automation.cereal_client.generated.resources.script_entry_no_match
import com.cereal_automation.cereal_client.generated.resources.script_entry_search
import org.jetbrains.compose.resources.stringResource

/**
 * Picks which of a package's variants to configure. Disabled when the entry is
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
    var triggerWidthPx by remember { mutableIntStateOf(0) }
    val close = {
        open = false
        query = ""
    }
    val label = stringResource(Res.string.script_entry_label)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CerealText(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                color = CerealTheme.colorScheme.contentSecondary,
            )
            CerealText(
                text = stringResource(Res.string.script_entry_count, options.size),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                color = CerealTheme.colorScheme.contentSubtle,
            )
        }
        Box {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .onSizeChanged { triggerWidthPx = it.width }
                        .clip(MediumShape)
                        .background(InputBg)
                        .border(1.dp, if (open) CerealTheme.colorScheme.borderDark else InputBorder, MediumShape)
                        .clickable(enabled = enabled) { open = true }
                        .semantics { contentDescription = label }
                        .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CerealText(
                    text = selected.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else CerealTheme.colorScheme.contentTertiary,
                )
                if (enabled) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = CerealTheme.colorScheme.contentSubtle,
                    )
                }
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = close,
                modifier = Modifier.width(with(LocalDensity.current) { triggerWidthPx.toDp() }),
                offset = DpOffset(0.dp, 6.dp),
                shape = MediumShape,
                containerColor = CerealTheme.colorScheme.cardDark,
                border = BorderStroke(1.dp, CerealTheme.colorScheme.borderDark),
                shadowElevation = 12.dp,
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (options.size > SEARCH_THRESHOLD) {
                        CerealTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = stringResource(Res.string.script_entry_search),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val matches = options.filter { it.name.contains(query.trim(), ignoreCase = true) }
                    Column(Modifier.heightIn(max = 264.dp).verticalScroll(rememberScrollState())) {
                        matches.forEach { option ->
                            EntryOptionRow(option, isSelected = option == selected) {
                                close()
                                onSelect(option)
                            }
                        }
                    }
                    if (matches.isEmpty()) {
                        CerealText(
                            text = stringResource(Res.string.script_entry_no_match, query),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = CerealTheme.colorScheme.contentSubtle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryOptionRow(
    option: ScriptEntryOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(SmallShape)
                .background(
                    when {
                        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_TINT)
                        hovered -> HoverBg
                        else -> Color.Transparent
                    },
                ).hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CerealText(
            text = option.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        )
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// Scrolling a short list beats typing into it; search only pays off past this many entries.
private const val SEARCH_THRESHOLD = 6

private const val SELECTED_TINT = 0.07f
private val MediumShape = RoundedCornerShape(8.dp)
private val SmallShape = RoundedCornerShape(6.dp)
private val HoverBg = Color(0xFF232323)

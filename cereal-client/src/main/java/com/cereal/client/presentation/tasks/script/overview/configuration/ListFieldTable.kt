package com.cereal.client.presentation.tasks.script.overview.configuration

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CaptionText
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealIconButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextButton
import com.cereal.client.presentation.view.ClickableUrlText
import com.cereal.client.presentation.view.VerticalDivider
import com.cereal.client.presentation.view.fields.DropdownTextField
import com.cereal.client.presentation.view.fields.MaskedVisualTransformation
import com.cereal.client.presentation.view.fields.SECRET_FIELD_REVEAL_TOGGLE_TAG
import com.cereal.client.presentation.view.fields.SwitchField
import com.cereal.client.presentation.view.fields.state.DropDownFieldState
import com.cereal.client.presentation.view.fields.state.FormFieldState
import com.cereal.client.presentation.view.fields.state.ListFieldState
import com.cereal.client.presentation.view.fields.state.ListRowState
import com.cereal.client.presentation.view.fields.state.SecretTextFieldState
import com.cereal.client.presentation.view.fields.state.SwitchFieldState
import com.cereal.client.presentation.view.fields.state.TextFieldState
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.add_row
import com.cereal_automation.cereal_client.generated.resources.import_from_csv
import com.cereal_automation.cereal_client.generated.resources.remove_entry
import com.cereal_automation.cereal_client.generated.resources.row_number
import com.cereal_automation.cereal_client.generated.resources.secret_field_hide
import com.cereal_automation.cereal_client.generated.resources.secret_field_reveal
import org.jetbrains.compose.resources.stringResource

private val NumberColumnWidth = 35.dp // 28px × 1.25
private val RemoveColumnWidth = 45.dp // 36px × 1.25
private val MinCellWidth = 150.dp // 120px × 1.25
private val CellHeight = 50.dp // 40px × 1.25
private val TableShape = RoundedCornerShape(15.dp) // 12px × 1.25

/**
 * A list configuration item as one table: a column per field of the record, a numbered row per entry.
 *
 * Nothing here knows what a script's record holds. Columns come from [ListFieldState.fieldDefinitions]
 * — header label, required marker and the field's description as the header tooltip — and each cell
 * renders its row's own field state, so a one-field list of SKUs and a four-field list of accounts are
 * the same table.
 */
@Composable
internal fun ListField(
    title: String,
    description: String?,
    isRequired: Boolean,
    state: ListFieldState,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel(title, isRequired)
            CerealText(
                text = "${state.completeRowCount()}/${state.rows.size}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = CerealTheme.colorScheme.contentTertiary,
            )
            Spacer(Modifier.weight(1f))
            if (state.showImportButton()) {
                CerealTextButton(onClick = { state.onImport() }, type = CerealButtonType.Neutral) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    CerealText(stringResource(Res.string.import_from_csv))
                }
            }
        }
        if (!description.isNullOrBlank()) {
            ClickableUrlText(description, color = CerealTheme.colorScheme.contentTertiary)
        }
        ListTable(state)
        ListErrors(state)
    }
}

/** Rows with every required field filled in — the "2/3" beside the list's label. */
private fun ListFieldState.completeRowCount(): Int =
    rows.count { row ->
        row.fieldDefinitions.all { it.isNullable || row.fieldStates[it.key]?.hasUserInput == true }
    }

@Composable
private fun ListTable(state: ListFieldState) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(TableShape)
                .border(1.dp, CerealTheme.colorScheme.border, TableShape)
                .background(CerealTheme.colorScheme.surfaceSunken),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fields = state.fieldDefinitions
            val minWidth = NumberColumnWidth + RemoveColumnWidth + MinCellWidth * fields.size
            // Columns share the width while they fit; past that each keeps its minimum and the table
            // scrolls sideways, so a record with many fields never squeezes its inputs unreadable.
            val scrolls = maxWidth < minWidth
            val cell: RowScope.() -> Modifier = { if (scrolls) Modifier.width(MinCellWidth) else Modifier.weight(1f) }
            Column(if (scrolls) Modifier.horizontalScroll(rememberScrollState()).width(minWidth) else Modifier) {
                HeaderRow(fields, cell)
                // ponytail: every row is composed eagerly; if lists of thousands of imported rows get slow,
                // make the screen's outer column a LazyColumn and emit the rows as its items.
                state.rows.forEachIndexed { index, row ->
                    key(row) { TableRow(state, row, index, cell) }
                }
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { state.addRow() }
                    .height(CellHeight)
                    .padding(horizontal = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = CerealTheme.colorScheme.contentTertiary, modifier = Modifier.size(18.dp))
            CerealText(
                text = stringResource(Res.string.add_row),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CerealTheme.colorScheme.contentTertiary,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeaderRow(
    fields: List<ScriptConfigurationItemDefinition>,
    cell: RowScope.() -> Modifier,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(CerealTheme.colorScheme.backgroundDark),
    ) {
        Spacer(Modifier.width(NumberColumnWidth))
        fields.forEach { field ->
            VerticalDivider(color = CerealTheme.colorScheme.divider)
            TooltipArea(
                modifier = cell(),
                tooltip = { field.description.takeIf { it.isNotBlank() }?.let { HeaderTooltip(it) } },
            ) {
                Row(Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
                    CerealText(
                        text = field.name.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = CerealTheme.colorScheme.contentTertiary,
                        maxLines = 1,
                    )
                    if (!field.isNullable) {
                        CerealText(
                            text = "*",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(RemoveColumnWidth))
    }
    HorizontalDivider(color = CerealTheme.colorScheme.divider)
}

@Composable
private fun HeaderTooltip(text: String) {
    Surface(shape = MaterialTheme.shapes.extraSmall) {
        CerealText(text = text, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TableRow(
    state: ListFieldState,
    row: ListRowState,
    index: Int,
    cell: RowScope.() -> Modifier,
) {
    val rowInteraction = remember { MutableInteractionSource() }
    val removeInteraction = remember { MutableInteractionSource() }
    val isHovered by rowInteraction.collectIsHoveredAsState()
    val isRemoveFocused by removeInteraction.collectIsFocusedAsState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .hoverable(rowInteraction),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CerealText(
            text = (index + 1).toString(),
            modifier = Modifier.width(NumberColumnWidth),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = CerealTheme.colorScheme.contentTertiary,
            textAlign = TextAlign.Center,
        )
        row.fieldDefinitions.forEach { field ->
            VerticalDivider(color = CerealTheme.colorScheme.divider)
            val fieldState = row.fieldStates[field.key]
            Box(
                modifier =
                    cell()
                        .fillMaxHeight()
                        .heightIn(min = CellHeight)
                        .then(if (fieldState?.showErrors() == true) Modifier.border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)) else Modifier),
                contentAlignment = Alignment.CenterStart,
            ) {
                fieldState?.let { TableCell(field, it) }
            }
        }
        VerticalDivider(color = CerealTheme.colorScheme.divider)
        // Hidden until the row is hovered, like the design — but still reachable by keyboard, and shown
        // while it has focus so a keyboard user can see what they are about to press.
        CerealIconButton(
            onClick = { state.removeRow(index) },
            modifier = Modifier.width(RemoveColumnWidth).alpha(if (isHovered || isRemoveFocused) 1f else 0f),
            interactionSource = removeInteraction,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(Res.string.remove_entry),
                tint = CerealTheme.colorScheme.contentTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    HorizontalDivider(color = CerealTheme.colorScheme.divider)
}

@Composable
private fun TableCell(
    field: ScriptConfigurationItemDefinition,
    state: FormFieldState<*, *>,
) {
    when (state) {
        // Before TextFieldState: a SecretTextFieldState is one, and must render masked.
        is SecretTextFieldState -> {
            val isRevealed by state.isRevealed
            Row(verticalAlignment = Alignment.CenterVertically) {
                CellTextInput(
                    field,
                    state,
                    Modifier.weight(1f),
                    if (isRevealed) VisualTransformation.None else MaskedVisualTransformation(),
                )
                if (state.text.isNotEmpty()) {
                    CerealIconButton(onClick = { state.toggleReveal() }, modifier = Modifier.size(35.dp).testTag(SECRET_FIELD_REVEAL_TOGGLE_TAG)) {
                        Icon(
                            imageVector = if (isRevealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = stringResource(if (isRevealed) Res.string.secret_field_hide else Res.string.secret_field_reveal),
                            tint = CerealTheme.colorScheme.contentTertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        is TextFieldState<*> -> {
            CellTextInput(field, state, Modifier.fillMaxWidth())
        }

        is SwitchFieldState -> {
            SwitchField(state, Modifier.padding(horizontal = 10.dp))
        }

        // ponytail: a dropdown cell reuses the outlined dropdown as-is; give it a borderless variant if
        // records with choice fields become common.
        is DropDownFieldState<*> -> {
            DropdownTextField(field.name, state, modifier = Modifier.padding(4.dp), labelVisible = false)
        }

        else -> {
            throw RuntimeException("Unsupported list field state ${state::class.simpleName} for '${field.key}'.")
        }
    }
}

/** A bare input filling its cell: the column header names it, so it carries the name only for screen readers. */
@Composable
private fun CellTextInput(
    field: ScriptConfigurationItemDefinition,
    state: TextFieldState<*>,
    modifier: Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val placeholder = state.placeholder.value
    BasicTextField(
        value = state.text,
        onValueChange = { state.onValueChange(it) },
        modifier =
            modifier
                .semantics { contentDescription = field.name }
                .onFocusChanged { state.onFocusChange(it.isFocused) },
        singleLine = true,
        textStyle = textStyle,
        visualTransformation = visualTransformation,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 15.dp), contentAlignment = Alignment.CenterStart) {
                if (state.text.isEmpty() && !placeholder.isNullOrBlank()) {
                    CerealText(placeholder, style = textStyle, color = CerealTheme.colorScheme.contentTertiary, maxLines = 1)
                }
                inner()
            }
        },
    )
}

/**
 * A cell has no room for its own error text, so it only turns red; the messages are listed here,
 * each naming its row and column, followed by the list's own error (e.g. too few rows).
 */
@Composable
private fun ListErrors(state: ListFieldState) {
    state.rows.forEachIndexed { index, row ->
        row.fieldDefinitions.forEach { field ->
            row.fieldStates[field.key]?.error?.let { error ->
                CaptionText(
                    "${stringResource(Res.string.row_number, index + 1)} · ${field.name}: $error",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    if (state.showErrors()) {
        state.error?.let { CaptionText(it, color = MaterialTheme.colorScheme.error) }
    }
}

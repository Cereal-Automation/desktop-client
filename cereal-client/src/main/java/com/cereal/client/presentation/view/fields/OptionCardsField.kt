package com.cereal.client.presentation.view.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.InputBorder
import com.cereal.client.presentation.view.fields.state.DropDownFieldState

/** Most options an enum may have to render as [OptionCardsField] instead of a dropdown. */
const val MAX_OPTION_CARDS = 3

/**
 * A [DropDownFieldState] as a row of option cards, each labelled with its option's `toString()`.
 * Clicking the selected card clears it when the state is clearable.
 */
@Composable
fun <T : Any> OptionCardsField(state: DropDownFieldState<T>) {
    FormField(state.error) {
        // FormField end-aligns its content; full width keeps the cards at the start, under the label.
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .selectableGroup()
                        .background(CerealTheme.colorScheme.inputBackground, RoundedCornerShape(8.dp))
                        .border(1.dp, InputBorder, RoundedCornerShape(8.dp))
                        .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                state.values.value.forEach { option ->
                    val selected = option == state.selectedValue
                    OptionCard(option.toString(), selected) { if (selected) state.onClear() else state.onValueChange(option) }
                }
            }
        }
    }
}

/** One card: raised when selected; red stays reserved for primary actions. */
@Composable
private fun OptionCard(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier =
            Modifier
                .height(30.dp)
                .clip(shape)
                .then(
                    if (selected) {
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, CerealTheme.colorScheme.borderDark, shape)
                    } else {
                        Modifier
                    },
                ).selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        CerealText(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) MaterialTheme.colorScheme.onSurface else CerealTheme.colorScheme.contentTertiary,
        )
    }
}

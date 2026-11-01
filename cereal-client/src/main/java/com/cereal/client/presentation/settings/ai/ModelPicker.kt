package com.cereal.client.presentation.settings.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealCircularProgressIndicator
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextField
import com.cereal.client.presentation.view.SettingsTextInput
import com.cereal.client.presentation.view.fields.FormField
import com.cereal.client.presentation.view.text
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.ai_model_free
import com.cereal_automation.cereal_client.generated.resources.ai_model_loading
import com.cereal_automation.cereal_client.generated.resources.ai_model_not_offered
import com.cereal_automation.cereal_client.generated.resources.ai_model_open
import com.cereal_automation.cereal_client.generated.resources.ai_model_pick
import com.cereal_automation.cereal_client.generated.resources.ai_model_pulled
import com.cereal_automation.cereal_client.generated.resources.ai_model_retry
import com.cereal_automation.cereal_client.generated.resources.ai_model_search
import com.cereal_automation.cereal_client.generated.resources.ai_model_use_custom
import com.cereal_automation.cereal_client.generated.resources.ai_model_waiting_key
import com.cereal_automation.cereal_client.generated.resources.ai_model_waiting_server
import org.jetbrains.compose.resources.stringResource

internal const val AI_MODEL_TAG = "ai_model"
internal const val AI_MODEL_OPEN_TAG = "ai_model_open"
internal const val AI_MODEL_SEARCH_TAG = "ai_model_search"
internal const val AI_MODEL_LIST_TAG = "ai_model_list"
internal const val AI_MODEL_USE_CUSTOM_TAG = "ai_model_use_custom"

/**
 * The model field shared by Settings → AI and the Home connect card. It follows [LlmPresetFormState.modelList]:
 * disabled until the connection is usable, a spinner while loading, a searchable combobox once loaded, and
 * free text with the reason and Retry when the load failed. A pick writes [LlmPresetFormState.modelState], and
 * its error (e.g. no model on save) shows under the field in every state.
 */
@Composable
fun ModelPicker(form: LlmPresetFormState) {
    Box(Modifier.fillMaxWidth().testTag(AI_MODEL_TAG)) {
        when (val list = form.modelList.value) {
            ModelListState.Idle -> {
                // A keyed preset with its key filled in is waiting on a usable base URL instead.
                val waitingForKey = form.preset.value?.needsKey == true && form.apiKeyState.text.isBlank()
                FormField(textError = form.modelState.error) {
                    DisabledField(stringResource(if (waitingForKey) Res.string.ai_model_waiting_key else Res.string.ai_model_waiting_server))
                }
            }

            ModelListState.Loading -> {
                FormField(textError = form.modelState.error) {
                    DisabledField(stringResource(Res.string.ai_model_loading)) {
                        CerealCircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                    }
                }
            }

            is ModelListState.Loaded -> {
                val preset = form.preset.value
                val models = remember(list, preset) { list.models.map { it.toUiModel(preset) } }
                FormField(textError = form.modelState.error) {
                    ModelCombobox(models, pulled = preset == LlmPreset.LOCAL, selectedId = form.modelState.text, onPick = form.modelState::onValueChange)
                }
            }

            is ModelListState.Failed -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsTextInput(state = form.modelState)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CerealText(
                            list.message.text(),
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        CerealText(
                            stringResource(Res.string.ai_model_retry),
                            modifier = Modifier.clickable(role = Role.Button, onClick = form::retry),
                            color = CerealTheme.colorScheme.link,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelCombobox(
    models: List<LlmModelUiModel>,
    pulled: Boolean,
    selectedId: String,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val close = {
        open = false
        query = ""
    }
    Box {
        ClosedField(selectedId, models, onClick = { open = true })
        DropdownMenu(
            expanded = open,
            onDismissRequest = close,
            properties = PopupProperties(focusable = true),
            modifier = Modifier.width(460.dp),
        ) {
            Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp).testTag(AI_MODEL_SEARCH_TAG)) {
                CerealTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(Res.string.ai_model_search),
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = CerealTheme.colorScheme.contentTertiary) },
                )
            }
            val visible = models.filter { it.matches(query) }
            // ponytail: not lazy; DropdownMenu measures intrinsics, which lazy lists refuse. Fine for a few hundred models.
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).testTag(AI_MODEL_LIST_TAG)) {
                visible.forEach { model ->
                    ModelRow(model, selected = model.id == selectedId, pulled = pulled) {
                        onPick(model.id)
                        close()
                    }
                }
                // The list isn't everything a provider serves (hidden models, partial lists): the query can be used as an id.
                val custom = query.trim()
                if (custom.isNotEmpty() && models.none { it.id == custom }) {
                    CerealText(
                        stringResource(Res.string.ai_model_use_custom, custom),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPick(custom)
                                    close()
                                }.testTag(AI_MODEL_USE_CUSTOM_TAG)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CerealTheme.colorScheme.link,
                    )
                }
            }
        }
    }
}

/** The closed combobox: the selected model, "Pick a model" when none, or the id with a warning when the list lacks it. */
@Composable
private fun ClosedField(
    selectedId: String,
    models: List<LlmModelUiModel>,
    onClick: () -> Unit,
) {
    val selected = models.firstOrNull { it.id == selectedId }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(FieldShape)
                .background(CerealTheme.colorScheme.inputBackground)
                .border(1.dp, CerealTheme.colorScheme.border, FieldShape)
                .clickable(role = Role.DropdownList, onClick = onClick)
                .testTag(AI_MODEL_OPEN_TAG)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            when {
                selected != null -> {
                    ModelLabel(selected)
                }

                selectedId.isBlank() -> {
                    CerealText(stringResource(Res.string.ai_model_pick), style = MaterialTheme.typography.bodyMedium, color = CerealTheme.colorScheme.contentTertiary)
                }

                else -> {
                    CerealText(selectedId, style = MaterialTheme.typography.bodyMedium)
                    CerealText(stringResource(Res.string.ai_model_not_offered), style = MaterialTheme.typography.bodySmall, color = CerealTheme.colorScheme.warning)
                }
            }
        }
        if (selected?.free == true) FreeBadge()
        Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = stringResource(Res.string.ai_model_open),
            modifier = Modifier.padding(start = 10.dp),
            tint = CerealTheme.colorScheme.contentTertiary,
        )
    }
}

@Composable
private fun ModelRow(
    model: LlmModelUiModel,
    selected: Boolean,
    pulled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_ROW_TINT) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) { ModelLabel(model) }
        if (model.free) FreeBadge()
        val date = model.date
        if (date != null) {
            // Local's date is when the model was pulled.
            CerealText(
                if (pulled) stringResource(Res.string.ai_model_pulled, date) else date,
                style = MaterialTheme.typography.bodySmall,
                color = CerealTheme.colorScheme.contentTertiary,
            )
        }
    }
}

/** What identifies a model in both the closed field and a row: the display name over the id, or just the id. */
@Composable
private fun ModelLabel(model: LlmModelUiModel) {
    CerealText(model.displayName ?: model.id, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
    if (model.displayName != null) {
        CerealText(model.id, style = MaterialTheme.typography.bodySmall, color = CerealTheme.colorScheme.contentTertiary)
    }
}

@Composable
private fun FreeBadge() {
    val green = CerealTheme.colorScheme.success
    val shape = RoundedCornerShape(4.dp)
    CerealText(
        stringResource(Res.string.ai_model_free),
        modifier =
            Modifier
                .padding(start = 10.dp)
                .clip(shape)
                .background(green.copy(alpha = FREE_BADGE_FILL))
                .border(1.dp, green.copy(alpha = FREE_BADGE_BORDER), shape)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        color = green,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun DisabledField(
    text: String,
    leading: (@Composable () -> Unit)? = null,
) = CerealTextField(
    value = "",
    onValueChange = {},
    placeholder = text,
    enabled = false,
    modifier = Modifier.fillMaxWidth(),
    leadingIcon = leading,
)

private fun LlmModelUiModel.matches(query: String) = id.contains(query, ignoreCase = true) || displayName?.contains(query, ignoreCase = true) == true

private const val SELECTED_ROW_TINT = 0.08f
private const val FREE_BADGE_FILL = 0.15f
private const val FREE_BADGE_BORDER = 0.4f
private val FieldShape = RoundedCornerShape(8.dp)

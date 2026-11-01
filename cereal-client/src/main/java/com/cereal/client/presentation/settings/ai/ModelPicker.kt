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
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealCircularProgressIndicator
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextField
import com.cereal.client.presentation.view.SettingsTextInput
import com.cereal.client.presentation.view.text
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.ai_model_loading
import com.cereal_automation.cereal_client.generated.resources.ai_model_not_offered
import com.cereal_automation.cereal_client.generated.resources.ai_model_open
import com.cereal_automation.cereal_client.generated.resources.ai_model_pick
import com.cereal_automation.cereal_client.generated.resources.ai_model_retry
import com.cereal_automation.cereal_client.generated.resources.ai_model_search
import com.cereal_automation.cereal_client.generated.resources.ai_model_waiting_key
import com.cereal_automation.cereal_client.generated.resources.ai_model_waiting_server
import org.jetbrains.compose.resources.stringResource

internal const val AI_MODEL_TAG = "ai_model"
internal const val AI_MODEL_OPEN_TAG = "ai_model_open"
internal const val AI_MODEL_SEARCH_TAG = "ai_model_search"
internal const val AI_MODEL_LIST_TAG = "ai_model_list"

/**
 * The model field shared by Settings → AI and the Home connect card. It follows [LlmPresetFormState.modelList]:
 * disabled until the connection is usable, a spinner while loading, a searchable combobox once loaded, and
 * free text with the reason and Retry when the load failed. A pick writes [LlmPresetFormState.modelState].
 */
@Composable
fun ModelPicker(
    form: LlmPresetFormState,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().testTag(AI_MODEL_TAG)) {
        when (val list = form.modelList.value) {
            ModelListState.Idle -> {
                val waitingFor = if (form.preset.value?.needsKey == false) Res.string.ai_model_waiting_server else Res.string.ai_model_waiting_key
                DisabledField(stringResource(waitingFor))
            }

            ModelListState.Loading -> {
                DisabledField(stringResource(Res.string.ai_model_loading)) {
                    CerealCircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                }
            }

            is ModelListState.Loaded -> {
                ModelCombobox(list.models, selectedId = form.modelState.text, onPick = form.modelState::onValueChange)
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
    models: List<LlmModel>,
    selectedId: String,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Box {
        ClosedField(selectedId, models, onClick = { open = true })
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
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
                    ModelRow(model, selected = model.id == selectedId) {
                        onPick(model.id)
                        open = false
                        query = ""
                    }
                }
            }
        }
    }
}

/** The closed combobox: the selected model, "Pick a model" when none, or the id with a warning when the list lacks it. */
@Composable
private fun ClosedField(
    selectedId: String,
    models: List<LlmModel>,
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
    model: LlmModel,
    selected: Boolean,
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
    }
}

/** What identifies a model in both the closed field and a row. */
@Composable
private fun ModelLabel(model: LlmModel) {
    CerealText(model.id, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
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

private fun LlmModel.matches(query: String) = id.contains(query, ignoreCase = true) || displayName?.contains(query, ignoreCase = true) == true

private const val SELECTED_ROW_TINT = 0.08f
private val FieldShape = RoundedCornerShape(8.dp)

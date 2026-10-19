package com.cereal.client.presentation.settings.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.ClickableUrlText
import com.cereal.client.presentation.view.ConfigurationPanel
import com.cereal.client.presentation.view.SettingsFieldRow
import com.cereal.client.presentation.view.SettingsTextInput
import com.cereal.client.presentation.view.fields.state.TextFieldState
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.ai_api_key
import com.cereal_automation.cereal_client.generated.resources.ai_api_key_desc_optional
import com.cereal_automation.cereal_client.generated.resources.ai_api_key_desc_required
import com.cereal_automation.cereal_client.generated.resources.ai_base_url
import com.cereal_automation.cereal_client.generated.resources.ai_base_url_desc
import com.cereal_automation.cereal_client.generated.resources.ai_free_tier_caveat
import com.cereal_automation.cereal_client.generated.resources.ai_get_key
import com.cereal_automation.cereal_client.generated.resources.ai_model
import com.cereal_automation.cereal_client.generated.resources.ai_model_change
import com.cereal_automation.cereal_client.generated.resources.ai_model_desc
import com.cereal_automation.cereal_client.generated.resources.ai_model_open
import com.cereal_automation.cereal_client.generated.resources.ai_preset_anthropic
import com.cereal_automation.cereal_client.generated.resources.ai_preset_anthropic_sub
import com.cereal_automation.cereal_client.generated.resources.ai_preset_gemini
import com.cereal_automation.cereal_client.generated.resources.ai_preset_gemini_sub
import com.cereal_automation.cereal_client.generated.resources.ai_preset_local
import com.cereal_automation.cereal_client.generated.resources.ai_preset_local_sub
import com.cereal_automation.cereal_client.generated.resources.ai_preset_openai
import com.cereal_automation.cereal_client.generated.resources.ai_preset_openai_sub
import com.cereal_automation.cereal_client.generated.resources.ai_preset_openrouter
import com.cereal_automation.cereal_client.generated.resources.ai_preset_openrouter_sub
import com.cereal_automation.cereal_client.generated.resources.ai_preset_other
import com.cereal_automation.cereal_client.generated.resources.ai_preset_other_sub
import com.cereal_automation.cereal_client.generated.resources.ai_privacy_line
import com.cereal_automation.cereal_client.generated.resources.ai_server
import com.cereal_automation.cereal_client.generated.resources.ai_server_desc
import com.cereal_automation.cereal_client.generated.resources.ai_spend_limits_anthropic
import com.cereal_automation.cereal_client.generated.resources.ai_spend_limits_gemini
import com.cereal_automation.cereal_client.generated.resources.ai_spend_limits_openai
import com.cereal_automation.cereal_client.generated.resources.ai_spend_limits_openrouter
import com.cereal_automation.cereal_client.generated.resources.ai_spend_limits_other
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal const val AI_BASE_URL_TAG = "ai_base_url"
internal const val AI_API_KEY_TAG = "ai_api_key"
internal const val AI_MODEL_TAG = "ai_model"
internal const val AI_MODEL_OPEN_TAG = "ai_model_open"

private val smallText @Composable get() = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp)

/**
 * The preset tiles plus the chosen preset's connection fields, free-tier caveat and privacy line. The
 * host adds its own actions (Save, Connect) below and routes tile clicks through [onSelectPreset].
 */
@Composable
fun LlmPresetForm(
    form: LlmPresetFormState,
    onSelectPreset: (LlmPreset) -> Unit = { form.select(it) },
) {
    val preset = form.preset.value
    PresetTiles(selected = preset, onSelect = onSelectPreset)
    if (preset != null) {
        ConfigurationPanel {
            PresetFields(preset, form)
            if (preset == LlmPreset.GEMINI) {
                CerealText("⚠ " + stringResource(Res.string.ai_free_tier_caveat), color = CerealTheme.colorScheme.warning, style = smallText)
            }
            preset.spendLimitsHelp()?.let { ClickableUrlText(stringResource(it), style = smallText) }
        }
    }
    CerealText(
        text = stringResource(Res.string.ai_privacy_line),
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        color = CerealTheme.colorScheme.contentTertiary,
        style = smallText,
    )
}

@Composable
private fun PresetFields(
    preset: LlmPreset,
    form: LlmPresetFormState,
) {
    when (preset) {
        LlmPreset.LOCAL -> {
            AiTextField(AI_BASE_URL_TAG, Res.string.ai_server, Res.string.ai_server_desc, form.baseUrlState)
            AiModelField(form.modelState, form.models.value)
        }

        LlmPreset.OTHER -> {
            AiTextField(AI_BASE_URL_TAG, Res.string.ai_base_url, Res.string.ai_base_url_desc, form.baseUrlState)
            AiTextField(AI_API_KEY_TAG, Res.string.ai_api_key, Res.string.ai_api_key_desc_optional, form.apiKeyState, isPassword = true)
            AiModelField(form.modelState, form.models.value)
        }

        else -> {
            if (form.customBaseUrl.value) {
                AiTextField(AI_BASE_URL_TAG, Res.string.ai_base_url, Res.string.ai_base_url_desc, form.baseUrlState)
            }
            AiTextField(AI_API_KEY_TAG, Res.string.ai_api_key, Res.string.ai_api_key_desc_required, form.apiKeyState, isPassword = true, keyUrl = preset.keyUrl)
            var changingModel by remember(preset) { mutableStateOf(false) }
            if (changingModel) {
                AiModelField(form.modelState, form.models.value)
            } else {
                ModelSummary(model = form.modelState.text, onChange = { changingModel = true })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetTiles(
    selected: LlmPreset?,
    onSelect: (LlmPreset) -> Unit,
) {
    // A 3-column grid that fills the card, whatever its width.
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.xs),
        maxItemsInEachRow = TILES_PER_ROW,
    ) {
        LlmPreset.entries.forEach { preset ->
            val isSelected = preset == selected
            val (name, sub) = preset.labels()
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(TileShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_TILE_TINT) else Color.Transparent)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else CerealTheme.colorScheme.border,
                            shape = TileShape,
                        ).selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(preset) })
                        .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                CerealText(stringResource(name), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                CerealText(stringResource(sub), color = CerealTheme.colorScheme.contentTertiary, style = smallText)
            }
        }
    }
}

@Composable
private fun ModelSummary(
    model: String,
    onChange: () -> Unit,
) {
    SettingsFieldRow(label = stringResource(Res.string.ai_model), description = stringResource(Res.string.ai_model_desc)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CerealText(model, style = MaterialTheme.typography.bodyMedium)
            CerealText(
                text = stringResource(Res.string.ai_model_change),
                modifier = Modifier.clickable(role = Role.Button, onClick = onChange),
                color = CerealTheme.colorScheme.link,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AiTextField(
    tag: String,
    label: StringResource,
    description: StringResource,
    state: TextFieldState<*>,
    isPassword: Boolean = false,
    keyUrl: String? = null,
) {
    val getKey = stringResource(Res.string.ai_get_key)
    SettingsFieldRow(
        label = stringResource(label),
        description = stringResource(description),
        help = keyUrl?.let { { ClickableUrlText("[$getKey]($it)", style = smallText) } },
    ) {
        Box(modifier = Modifier.testTag(tag)) {
            SettingsTextInput(state = state, isPassword = isPassword)
        }
    }
}

/**
 * Editable model combobox: free text always, plus the fetched [models] filtered by what's typed. With
 * no list (not fetched, or the fetch failed) it is a plain text field.
 */
@Composable
private fun AiModelField(
    state: TextFieldState<*>,
    models: List<String>,
) {
    var expanded by remember { mutableStateOf(false) }
    SettingsFieldRow(label = stringResource(Res.string.ai_model), description = stringResource(Res.string.ai_model_desc)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f).testTag(AI_MODEL_TAG)) {
                    SettingsTextInput(state = state)
                }
                if (models.isNotEmpty()) {
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag(AI_MODEL_OPEN_TAG)) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(Res.string.ai_model_open))
                    }
                }
            }
            // A picked id shows the whole list again; other text filters it.
            val visible = if (state.text in models) models else models.filter { it.contains(state.text, ignoreCase = true) }
            DropdownMenu(
                expanded = expanded && visible.isNotEmpty(),
                onDismissRequest = { expanded = false },
                // Not focusable, so typing in the field keeps filtering while the menu is open.
                properties = PopupProperties(focusable = false),
            ) {
                visible.forEach { id ->
                    DropdownMenuItem(
                        onClick = {
                            expanded = false
                            state.onValueChange(id)
                        },
                        text = { CerealText(id, style = MaterialTheme.typography.bodyMedium) },
                    )
                }
            }
        }
    }
}

private const val TILES_PER_ROW = 3
private const val SELECTED_TILE_TINT = 0.08f
private val TileShape = RoundedCornerShape(8.dp)

internal fun LlmPreset.labels(): Pair<StringResource, StringResource> =
    when (this) {
        LlmPreset.GEMINI -> Res.string.ai_preset_gemini to Res.string.ai_preset_gemini_sub
        LlmPreset.ANTHROPIC -> Res.string.ai_preset_anthropic to Res.string.ai_preset_anthropic_sub
        LlmPreset.OPENAI -> Res.string.ai_preset_openai to Res.string.ai_preset_openai_sub
        LlmPreset.OPENROUTER -> Res.string.ai_preset_openrouter to Res.string.ai_preset_openrouter_sub
        LlmPreset.LOCAL -> Res.string.ai_preset_local to Res.string.ai_preset_local_sub
        LlmPreset.OTHER -> Res.string.ai_preset_other to Res.string.ai_preset_other_sub
    }

/** Where to cap spend for [this] preset; null for [LlmPreset.LOCAL], which costs nothing. */
private fun LlmPreset.spendLimitsHelp(): StringResource? =
    when (this) {
        LlmPreset.GEMINI -> Res.string.ai_spend_limits_gemini
        LlmPreset.ANTHROPIC -> Res.string.ai_spend_limits_anthropic
        LlmPreset.OPENAI -> Res.string.ai_spend_limits_openai
        LlmPreset.OPENROUTER -> Res.string.ai_spend_limits_openrouter
        LlmPreset.LOCAL -> null
        LlmPreset.OTHER -> Res.string.ai_spend_limits_other
    }

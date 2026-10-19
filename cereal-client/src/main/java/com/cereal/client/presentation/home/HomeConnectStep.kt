package com.cereal.client.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.presentation.settings.ai.AI_API_KEY_TAG
import com.cereal.client.presentation.settings.ai.AI_BASE_URL_TAG
import com.cereal.client.presentation.settings.ai.AI_MODEL_TAG
import com.cereal.client.presentation.settings.ai.LlmPreset
import com.cereal.client.presentation.settings.ai.label
import com.cereal.client.presentation.settings.ai.spendLimitsHelp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.ClickableUrlText
import com.cereal.client.presentation.view.SettingsButtonVariant
import com.cereal.client.presentation.view.SettingsSmallButton
import com.cereal.client.presentation.view.fields.state.TextFieldState
import com.cereal.client.presentation.view.text
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.ai_api_key
import com.cereal_automation.cereal_client.generated.resources.ai_base_url
import com.cereal_automation.cereal_client.generated.resources.ai_get_key
import com.cereal_automation.cereal_client.generated.resources.ai_model
import com.cereal_automation.cereal_client.generated.resources.home_ai_change
import com.cereal_automation.cereal_client.generated.resources.home_ai_checking
import com.cereal_automation.cereal_client.generated.resources.home_ai_connect
import com.cereal_automation.cereal_client.generated.resources.home_ai_connect_hint
import com.cereal_automation.cereal_client.generated.resources.home_ai_connect_hint_local
import com.cereal_automation.cereal_client.generated.resources.home_ai_connected
import com.cereal_automation.cereal_client.generated.resources.home_ai_hint_gemini
import com.cereal_automation.cereal_client.generated.resources.home_ai_hint_local
import com.cereal_automation.cereal_client.generated.resources.home_ai_hint_openrouter
import com.cereal_automation.cereal_client.generated.resources.home_ai_hint_other
import com.cereal_automation.cereal_client.generated.resources.home_ai_hint_paid
import com.cereal_automation.cereal_client.generated.resources.home_ai_key_help
import com.cereal_automation.cereal_client.generated.resources.home_ai_model_help
import com.cereal_automation.cereal_client.generated.resources.home_ai_note_local
import com.cereal_automation.cereal_client.generated.resources.home_ai_privacy
import com.cereal_automation.cereal_client.generated.resources.home_ai_privacy_local
import com.cereal_automation.cereal_client.generated.resources.home_ai_server
import com.cereal_automation.cereal_client.generated.resources.home_ai_setup_subtitle
import com.cereal_automation.cereal_client.generated.resources.home_ai_setup_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Step 1 with no provider yet: provider tiles, the chosen provider's fields, and Connect. */
@Composable
internal fun ConnectStep(viewModel: HomeAiProviderViewModel) {
    val form = viewModel.setup
    val preset = form.preset.value ?: LlmPreset.GEMINI
    val isLocal = preset == LlmPreset.LOCAL
    val connectError = viewModel.connectError.value?.text()
    val connecting = viewModel.isConnecting.value
    HomeStep(
        number = 1,
        title = stringResource(Res.string.home_ai_setup_title),
        subtitle = { StepSubtitle(stringResource(Res.string.home_ai_setup_subtitle)) },
    ) {
        Column(modifier = StepBodyPadding, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ProviderTiles(selected = preset, onSelect = viewModel::onSelectPreset)
            HorizontalDivider(thickness = 1.dp, color = Divider)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (isLocal || preset == LlmPreset.OTHER) {
                    StepField(stringResource(if (isLocal) Res.string.home_ai_server else Res.string.ai_base_url)) {
                        FormInput(form.baseUrlState, AI_BASE_URL_TAG, if (isLocal) "http://localhost:11434/v1" else "https://api.example.com/v1")
                    }
                }
                if (!isLocal) {
                    StepField(
                        label = stringResource(Res.string.ai_api_key),
                        trailing = { preset.keyUrl?.let { ClickableUrlText("[${stringResource(Res.string.ai_get_key)}]($it)", style = helpStyle.copy(fontSize = 12.5.sp)) } },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val error = form.apiKeyState.error ?: connectError
                            FormInput(form.apiKeyState, AI_API_KEY_TAG, keyPlaceholder(preset), isPassword = true, isError = error != null)
                            FieldHelp(error ?: stringResource(Res.string.home_ai_key_help), isError = error != null)
                        }
                    }
                }
                StepField(stringResource(Res.string.ai_model)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FormInput(form.modelState, AI_MODEL_TAG, "model-id")
                        FieldHelp(form.modelState.error ?: stringResource(Res.string.home_ai_model_help), isError = form.modelState.error != null)
                    }
                }
                val note = if (isLocal) Res.string.home_ai_note_local else preset.spendLimitsHelp()
                note?.let {
                    HomeNote {
                        ClickableUrlText(
                            stringResource(it),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 19.sp),
                            color = CerealTheme.colorScheme.contentTertiary,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CerealButton(onClick = viewModel::onConnect, enabled = !connecting) {
                    if (connecting) {
                        CircularProgressIndicator(modifier = Modifier.size(13.dp), color = Color.White, strokeWidth = 2.dp)
                        Box(Modifier.size(7.dp))
                    }
                    CerealText(stringResource(if (connecting) Res.string.home_ai_checking else Res.string.home_ai_connect))
                }
                // A local server has no key field, so its connection error shows here.
                val localError = connectError.takeIf { isLocal }
                CerealText(
                    text = localError ?: stringResource(if (isLocal) Res.string.home_ai_connect_hint_local else Res.string.home_ai_connect_hint),
                    style = helpStyle,
                    color = if (localError != null) MaterialTheme.colorScheme.primary else CerealTheme.colorScheme.contentSubtle,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(13.dp), tint = CerealTheme.colorScheme.contentSubtle)
                val target = if (isLocal) stringResource(Res.string.home_ai_privacy_local) else stringResource(preset.label())
                CerealText(stringResource(Res.string.home_ai_privacy, target), style = helpStyle, color = CerealTheme.colorScheme.contentSubtle)
            }
        }
    }
}

@Composable
private fun FormInput(
    state: TextFieldState<*>,
    tag: String,
    placeholder: String,
    isPassword: Boolean = false,
    isError: Boolean = state.error != null,
) {
    // Tagged on a wrapper, as Settings → AI does, so tests find the field by ancestor.
    Box(modifier = Modifier.fillMaxWidth().testTag(tag)) {
        HomeInput(
            value = state.text,
            onValueChange = state::onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder,
            isError = isError,
            isPassword = isPassword,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderTiles(
    selected: LlmPreset,
    onSelect: (LlmPreset) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = TILES_PER_ROW,
    ) {
        LlmPreset.entries.forEach { preset ->
            val isSelected = preset == selected
            val primary = MaterialTheme.colorScheme.primary
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(TileShape)
                        .background(if (isSelected) primary.copy(alpha = SELECTED_TILE_TINT) else MaterialTheme.colorScheme.background)
                        .border(1.dp, if (isSelected) primary else CerealTheme.colorScheme.border, TileShape)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(preset) })
                        .padding(horizontal = 13.dp, vertical = 11.dp),
            ) {
                Column(modifier = Modifier.padding(end = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CerealText(
                        stringResource(preset.label()),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    CerealText(
                        stringResource(preset.hint()),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = if (isSelected) CerealTheme.colorScheme.contentSecondary else CerealTheme.colorScheme.contentTertiary,
                    )
                }
                // The radio dot: a ring, filled thick when selected.
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 1.dp)
                            .size(14.dp)
                            .border(if (isSelected) 4.dp else 1.5.dp, if (isSelected) primary else CerealTheme.colorScheme.borderDark, CircleShape),
                )
            }
        }
    }
}

/** Step 1 done: the provider and model, with Change opening Settings → AI. */
@Composable
internal fun ConnectedStep(
    provider: AiProviderUiModel,
    onChange: () -> Unit,
) {
    HomeStep(
        number = 1,
        badge = StepBadge.Done,
        title = stringResource(Res.string.home_ai_connected),
        subtitle = {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                CerealText(stringResource(provider.preset.label()), style = style.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                Box(Modifier.size(3.dp).clip(CircleShape).background(CerealTheme.colorScheme.contentSubtle))
                CerealText(provider.model, style = style, color = CerealTheme.colorScheme.contentSecondary)
            }
        },
        trailing = { SettingsSmallButton(text = stringResource(Res.string.home_ai_change), onClick = onChange, variant = SettingsButtonVariant.Ghost) },
    )
}

private fun LlmPreset.hint(): StringResource =
    when (this) {
        LlmPreset.GEMINI -> Res.string.home_ai_hint_gemini
        LlmPreset.ANTHROPIC, LlmPreset.OPENAI -> Res.string.home_ai_hint_paid
        LlmPreset.OPENROUTER -> Res.string.home_ai_hint_openrouter
        LlmPreset.LOCAL -> Res.string.home_ai_hint_local
        LlmPreset.OTHER -> Res.string.home_ai_hint_other
    }

private fun keyPlaceholder(preset: LlmPreset): String =
    when (preset) {
        LlmPreset.GEMINI -> "AIza…"
        LlmPreset.ANTHROPIC -> "sk-ant-…"
        LlmPreset.OPENAI -> "sk-…"
        LlmPreset.OPENROUTER -> "sk-or-…"
        LlmPreset.LOCAL, LlmPreset.OTHER -> ""
    }

private const val TILES_PER_ROW = 3
private const val SELECTED_TILE_TINT = 0.07f
private val TileShape = RoundedCornerShape(8.dp)

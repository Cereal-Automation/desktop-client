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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
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
import com.cereal.client.presentation.settings.ai.AI_API_KEY_TAG
import com.cereal.client.presentation.settings.ai.AI_BASE_URL_TAG
import com.cereal.client.presentation.settings.ai.AI_MODEL_TAG
import com.cereal.client.presentation.settings.ai.LlmPreset
import com.cereal.client.presentation.settings.ai.label
import com.cereal.client.presentation.settings.ai.spendLimitsHelp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealCircularProgressIndicator
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.ClickableUrlText
import com.cereal.client.presentation.view.text
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.ai_api_key
import com.cereal_automation.cereal_client.generated.resources.ai_api_key_desc_optional
import com.cereal_automation.cereal_client.generated.resources.ai_base_url
import com.cereal_automation.cereal_client.generated.resources.ai_get_key
import com.cereal_automation.cereal_client.generated.resources.ai_model
import com.cereal_automation.cereal_client.generated.resources.ai_server
import com.cereal_automation.cereal_client.generated.resources.ai_server_desc
import com.cereal_automation.cereal_client.generated.resources.home_ai_checking
import com.cereal_automation.cereal_client.generated.resources.home_ai_connect
import com.cereal_automation.cereal_client.generated.resources.home_ai_connect_hint
import com.cereal_automation.cereal_client.generated.resources.home_ai_connect_hint_local
import com.cereal_automation.cereal_client.generated.resources.home_ai_key_help
import com.cereal_automation.cereal_client.generated.resources.home_ai_model_help
import com.cereal_automation.cereal_client.generated.resources.home_ai_privacy
import com.cereal_automation.cereal_client.generated.resources.home_ai_privacy_local
import com.cereal_automation.cereal_client.generated.resources.home_ai_privacy_other
import org.jetbrains.compose.resources.stringResource

/** Step 1's body: provider tiles, the chosen provider's fields, its spend note, Connect, and where the key goes. */
@Composable
internal fun HomeConnectForm(viewModel: HomeAiProviderViewModel) {
    val form = viewModel.setup
    val preset = form.preset.value ?: return
    val connecting = viewModel.isConnecting.value
    val connectError = viewModel.connectError.value?.text()
    val hasKey = preset != LlmPreset.LOCAL
    Column(
        modifier = Modifier.padding(start = 62.dp, end = 22.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ProviderTiles(selected = preset, onSelect = viewModel::onSelectPreset)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HorizontalDivider(thickness = 1.dp, color = CerealTheme.colorScheme.border, modifier = Modifier.padding(bottom = 4.dp))
            if (!hasKey || preset == LlmPreset.OTHER || form.customBaseUrl.value) {
                StepField(stringResource(if (hasKey) Res.string.ai_base_url else Res.string.ai_server)) {
                    ComposerField(
                        value = form.baseUrlState.text,
                        onValueChange = form.baseUrlState::onValueChange,
                        error = form.baseUrlState.error,
                        help = if (hasKey) null else stringResource(Res.string.ai_server_desc),
                        placeholder = if (hasKey) "https://api.example.com/v1" else LlmPreset.LOCAL.baseUrl,
                        modifier = Modifier.fillMaxWidth().testTag(AI_BASE_URL_TAG),
                    )
                }
            }
            if (hasKey) {
                StepField(
                    label = stringResource(Res.string.ai_api_key),
                    trailing = preset.keyUrl?.let { url -> { ClickableUrlText("[${stringResource(Res.string.ai_get_key)}]($url)") } },
                ) {
                    ComposerField(
                        value = form.apiKeyState.text,
                        onValueChange = form.apiKeyState::onValueChange,
                        error = form.apiKeyState.error ?: connectError,
                        help = stringResource(if (preset == LlmPreset.OTHER) Res.string.ai_api_key_desc_optional else Res.string.home_ai_key_help),
                        isPassword = true,
                        modifier = Modifier.fillMaxWidth().testTag(AI_API_KEY_TAG),
                    )
                }
            }
            StepField(stringResource(Res.string.ai_model)) {
                // PROTOTYPE (wayfinder #249)
                com.cereal.client.presentation.settings.ai.ModelPickerPrototype(compact = true)
            }
            preset.spendLimitsHelp()?.let { SpendNote(stringResource(it)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CerealButton(onClick = viewModel::onConnect, enabled = !connecting) {
                if (connecting) {
                    CerealCircularProgressIndicator(modifier = Modifier.size(13.dp), color = Color.White, strokeWidth = 2.dp)
                    CerealText(stringResource(Res.string.home_ai_checking), modifier = Modifier.padding(start = 8.dp))
                } else {
                    CerealText(stringResource(Res.string.home_ai_connect))
                }
            }
            // With no key field, a failed connect has nowhere else to show.
            val footError = connectError.takeUnless { hasKey }
            CerealText(
                text = footError ?: stringResource(if (hasKey) Res.string.home_ai_connect_hint else Res.string.home_ai_connect_hint_local),
                style = MaterialTheme.typography.bodySmall,
                color = if (footError != null) MaterialTheme.colorScheme.error else CerealTheme.colorScheme.contentTertiary,
            )
        }
        PrivacyLine(preset)
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
        maxItemsInEachRow = 3,
    ) {
        LlmPreset.entries.forEach { preset ->
            val on = preset == selected
            val primary = MaterialTheme.colorScheme.primary
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(TileShape)
                        .background(if (on) primary.copy(alpha = 0.07f) else MaterialTheme.colorScheme.background)
                        .border(1.dp, if (on) primary else CerealTheme.colorScheme.border, TileShape)
                        .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(preset) })
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CerealText(
                    stringResource(preset.label()),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Box(
                    Modifier
                        .size(14.dp)
                        .border(if (on) 4.dp else 1.5.dp, if (on) primary else CerealTheme.colorScheme.borderDark, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun SpendNote(text: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(TileShape)
                .background(CerealTheme.colorScheme.inputBackground)
                .border(1.dp, CerealTheme.colorScheme.border, TileShape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(14.dp), tint = CerealTheme.colorScheme.contentTertiary)
        ClickableUrlText(text)
    }
}

@Composable
private fun PrivacyLine(preset: LlmPreset) {
    val target =
        when (preset) {
            LlmPreset.LOCAL -> stringResource(Res.string.home_ai_privacy_local)
            LlmPreset.OTHER -> stringResource(Res.string.home_ai_privacy_other)
            else -> stringResource(preset.label())
        }
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(13.dp), tint = CerealTheme.colorScheme.contentTertiary)
        CerealText(stringResource(Res.string.home_ai_privacy, target), style = MaterialTheme.typography.bodySmall, color = CerealTheme.colorScheme.contentTertiary)
    }
}

private val TileShape = RoundedCornerShape(8.dp)

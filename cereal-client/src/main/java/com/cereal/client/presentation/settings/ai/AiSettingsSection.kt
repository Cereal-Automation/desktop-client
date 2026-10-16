package com.cereal.client.presentation.settings.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.ConfigurationPanel
import com.cereal.client.presentation.view.SettingsButtonVariant
import com.cereal.client.presentation.view.SettingsFieldRow
import com.cereal.client.presentation.view.SettingsSectionCard
import com.cereal.client.presentation.view.SettingsSmallButton
import com.cereal.client.presentation.view.SettingsTextInput
import com.cereal.client.presentation.view.fields.state.TextFieldState
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.ai
import com.cereal_automation.cereal_client.generated.resources.ai_api_key
import com.cereal_automation.cereal_client.generated.resources.ai_api_key_desc_optional
import com.cereal_automation.cereal_client.generated.resources.ai_api_key_desc_required
import com.cereal_automation.cereal_client.generated.resources.ai_base_url
import com.cereal_automation.cereal_client.generated.resources.ai_base_url_desc
import com.cereal_automation.cereal_client.generated.resources.ai_model
import com.cereal_automation.cereal_client.generated.resources.ai_model_desc
import com.cereal_automation.cereal_client.generated.resources.ai_provider
import com.cereal_automation.cereal_client.generated.resources.ai_provider_anthropic
import com.cereal_automation.cereal_client.generated.resources.ai_provider_desc
import com.cereal_automation.cereal_client.generated.resources.ai_provider_none
import com.cereal_automation.cereal_client.generated.resources.ai_provider_openai_compatible
import com.cereal_automation.cereal_client.generated.resources.ai_spend_limits_anthropic
import com.cereal_automation.cereal_client.generated.resources.ai_spend_limits_openai_compatible
import com.cereal_automation.cereal_client.generated.resources.save
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent

internal const val AI_BASE_URL_TAG = "ai_base_url"
internal const val AI_API_KEY_TAG = "ai_api_key"
internal const val AI_MODEL_TAG = "ai_model"

/** Settings → AI: pick an LLM provider; anything but None reveals its connection fields. */
@Composable
fun AiSettingsSection(vm: AiSettingsViewModel = rememberAiSettingsViewModel()) {
    val provider = vm.provider.value
    SettingsSectionCard(title = stringResource(Res.string.ai)) {
        ProviderRow(selected = provider, onSelect = vm::selectProvider)
        if (provider != null) {
            ConfigurationPanel {
                AiTextField(AI_BASE_URL_TAG, Res.string.ai_base_url, Res.string.ai_base_url_desc, vm.baseUrlState)
                AiTextField(
                    tag = AI_API_KEY_TAG,
                    label = Res.string.ai_api_key,
                    description = if (provider.requiresApiKey) Res.string.ai_api_key_desc_required else Res.string.ai_api_key_desc_optional,
                    state = vm.apiKeyState,
                    isPassword = true,
                )
                AiTextField(AI_MODEL_TAG, Res.string.ai_model, Res.string.ai_model_desc, vm.modelState)
                CerealText(
                    text = stringResource(provider.spendLimitsHelp()),
                    color = CerealTheme.colorScheme.contentTertiary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            SettingsSmallButton(
                text = stringResource(Res.string.save),
                onClick = vm::save,
                variant = SettingsButtonVariant.Primary,
            )
        }
    }
}

@Composable
fun rememberAiSettingsViewModel(): AiSettingsViewModel {
    val coroutineScope = rememberCoroutineScope()
    return remember {
        KoinJavaComponent.get(AiSettingsViewModel::class.java, parameters = { parametersOf(coroutineScope) })
    }
}

@Composable
private fun AiTextField(
    tag: String,
    label: StringResource,
    description: StringResource,
    state: TextFieldState<*>,
    isPassword: Boolean = false,
) {
    SettingsFieldRow(label = stringResource(label), description = stringResource(description)) {
        Box(modifier = Modifier.testTag(tag)) {
            SettingsTextInput(state = state, isPassword = isPassword)
        }
    }
}

@Composable
private fun ProviderRow(
    selected: LlmProviderType?,
    onSelect: (LlmProviderType?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CerealText(
                text = stringResource(Res.string.ai_provider),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
            )
            Spacer(modifier = Modifier.size(4.dp))
            CerealText(
                text = stringResource(Res.string.ai_provider_desc),
                color = CerealTheme.colorScheme.contentTertiary,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
            )
        }
        Spacer(modifier = Modifier.size(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf(null) + LlmProviderType.entries).forEach { option ->
                SettingsSmallButton(
                    text = stringResource(option.label()),
                    onClick = { onSelect(option) },
                    variant = if (option == selected) SettingsButtonVariant.Primary else SettingsButtonVariant.Flat,
                )
            }
        }
    }
}

private fun LlmProviderType?.label(): StringResource =
    when (this) {
        null -> Res.string.ai_provider_none
        LlmProviderType.ANTHROPIC -> Res.string.ai_provider_anthropic
        LlmProviderType.OPENAI_COMPATIBLE -> Res.string.ai_provider_openai_compatible
    }

private fun LlmProviderType.spendLimitsHelp(): StringResource =
    when (this) {
        LlmProviderType.ANTHROPIC -> Res.string.ai_spend_limits_anthropic
        LlmProviderType.OPENAI_COMPATIBLE -> Res.string.ai_spend_limits_openai_compatible
    }

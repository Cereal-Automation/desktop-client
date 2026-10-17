package com.cereal.client.presentation.settings.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.ConfigurationPanel
import com.cereal.client.presentation.view.SettingsButtonVariant
import com.cereal.client.presentation.view.SettingsFieldRow
import com.cereal.client.presentation.view.SettingsSectionCard
import com.cereal.client.presentation.view.SettingsSectionRow
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
import com.cereal_automation.cereal_client.generated.resources.ai_model_open
import com.cereal_automation.cereal_client.generated.resources.ai_provider
import com.cereal_automation.cereal_client.generated.resources.ai_provider_anthropic
import com.cereal_automation.cereal_client.generated.resources.ai_provider_desc
import com.cereal_automation.cereal_client.generated.resources.ai_provider_none
import com.cereal_automation.cereal_client.generated.resources.ai_provider_openai_compatible
import com.cereal_automation.cereal_client.generated.resources.ai_spend_limits_anthropic
import com.cereal_automation.cereal_client.generated.resources.ai_spend_limits_openai_compatible
import com.cereal_automation.cereal_client.generated.resources.ai_test_connection
import com.cereal_automation.cereal_client.generated.resources.save
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent

internal const val AI_BASE_URL_TAG = "ai_base_url"
internal const val AI_API_KEY_TAG = "ai_api_key"
internal const val AI_MODEL_TAG = "ai_model"
internal const val AI_MODEL_OPEN_TAG = "ai_model_open"

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
                AiModelField(vm.modelState, vm.models.value)
                CerealText(
                    text = stringResource(provider.spendLimitsHelp()),
                    color = CerealTheme.colorScheme.contentTertiary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val status = vm.connectionStatus.value
            CerealText(
                text = status.message,
                modifier = Modifier.weight(1f),
                color = if (status is ConnectionStatus.Failed) MaterialTheme.colorScheme.error else CerealTheme.colorScheme.contentTertiary,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
            )
            if (provider != null) {
                SettingsSmallButton(
                    text = stringResource(Res.string.ai_test_connection),
                    onClick = vm::testConnection,
                    enabled = status != ConnectionStatus.Testing,
                )
            }
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

@Composable
private fun ProviderRow(
    selected: LlmProviderType?,
    onSelect: (LlmProviderType?) -> Unit,
) {
    SettingsSectionRow(
        title = stringResource(Res.string.ai_provider),
        description = stringResource(Res.string.ai_provider_desc),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.xs)) {
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
